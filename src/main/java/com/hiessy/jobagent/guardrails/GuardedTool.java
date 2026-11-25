package com.hiessy.jobagent.guardrails;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.guardrails.AgentRunContext.ToolStep;
import com.hiessy.jobagent.observability.AgentMetrics;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

/**
 * Wraps one {@link ToolSpec} as a Spring AI {@link ToolCallback}. Every call, from the agent loop or
 * from an MCP client, goes through the same steps: taint check for write tools, JSON parsing into
 * the input record, Jakarta validation, the handler, then metrics and the run trace. The model gets
 * a JSON answer in every case, never an exception.
 */
public final class GuardedTool<I> implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(GuardedTool.class);

    private final ToolSpec<I> spec;
    private final ToolDefinition definition;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final AgentMetrics metrics;
    private final int maxCallsPerRun;

    public GuardedTool(ToolSpec<I> spec, ObjectMapper mapper, Validator validator, AgentMetrics metrics, int maxCallsPerRun) {
        this.spec = spec;
        this.mapper = mapper.copy().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.validator = validator;
        this.metrics = metrics;
        this.maxCallsPerRun = maxCallsPerRun;
        this.definition = ToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(JsonSchemaGenerator.generateForType(spec.inputType()))
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    public boolean writes() {
        return spec.writes();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        AgentRunContext run = AgentRunContext.from(toolContext);
        long start = System.nanoTime();
        ToolOutput out;
        try {
            out = guardedCall(toolInput, run);
        } catch (ToolInputException e) {
            metrics.guardrail("invalid_input");
            out = ToolOutput.Problem.invalid(List.of(e.getMessage()));
        } catch (RuntimeException e) {
            log.warn("Tool {} failed", spec.name(), e);
            out = ToolOutput.Problem.error(spec.name() + " failed: " + e.getMessage());
        }
        if (out instanceof ToolOutput.Problem p && p.isInjection() && !run.tainted()) {
            metrics.guardrail(ToolOutput.Problem.PROMPT_INJECTION);
            run.taint(p.reason());
        }
        String json = toJson(out);
        String outcome = out.status().toLowerCase();
        long nanos = System.nanoTime() - start;
        metrics.toolCall(spec.name(), outcome, run.channel(), nanos);
        run.record(new ToolStep(spec.name(), toolInput, outcome, nanos / 1_000_000, cut(json, 500)));
        return json;
    }

    private ToolOutput guardedCall(String toolInput, AgentRunContext run) {
        if (run.nextToolCall() > maxCallsPerRun) {
            metrics.guardrail("tool_budget");
            return ToolOutput.Problem.refused("tool_budget", "Tool budget of " + maxCallsPerRun + " calls for this run is used up.");
        }
        if (spec.writes() && run.tainted()) {
            metrics.guardrail("tainted_write");
            return ToolOutput.Problem.refused("tainted_write", "Blocked: this run read text that tried to instruct the agent ("
                    + run.taintReason() + "). Write tools stay off for the rest of the run.");
        }
        I input = parse(toolInput);
        List<String> errors = validator.validate(input).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(GuardedTool::describe)
                .toList();
        if (!errors.isEmpty()) {
            metrics.guardrail("invalid_input");
            return ToolOutput.Problem.invalid(errors);
        }
        return spec.handler().apply(input, run);
    }

    private I parse(String toolInput) {
        String json = toolInput == null || toolInput.isBlank() ? "{}" : toolInput;
        try {
            return mapper.readValue(json, spec.inputType());
        } catch (Exception e) {
            throw new ToolInputException("arguments are not a JSON object matching the tool schema");
        }
    }

    private static String describe(ConstraintViolation<?> v) {
        String field = v.getPropertyPath().toString().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        return field + ": " + v.getMessage();
    }

    private String toJson(ToolOutput out) {
        try {
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"reason\":\"could not serialise the result\"}";
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
