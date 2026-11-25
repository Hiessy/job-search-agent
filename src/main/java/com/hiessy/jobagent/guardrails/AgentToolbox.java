package com.hiessy.jobagent.guardrails;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.config.JobAgentProperties;
import com.hiessy.jobagent.guardrails.AgentRunContext.ToolStep;
import com.hiessy.jobagent.observability.AgentMetrics;
import jakarta.validation.Validator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * The tool allowlist. Only tools named in {@code jobagent.guardrails.allowed-tools} are offered to
 * the model and to MCP clients; a call to any other name (a hallucinated tool, or one switched off)
 * gets an error back instead of running.
 */
@Component
public class AgentToolbox {

    private static final Logger log = LoggerFactory.getLogger(AgentToolbox.class);

    private final Map<String, GuardedTool<?>> tools = new LinkedHashMap<>();
    private final AgentMetrics metrics;
    private final ObjectMapper mapper;

    public AgentToolbox(List<ToolSpec<?>> specs, JobAgentProperties props, ObjectMapper mapper, Validator validator,
                        AgentMetrics metrics) {
        this.metrics = metrics;
        this.mapper = mapper;
        List<String> allowed = props.guardrails().allowedTools();
        for (ToolSpec<?> spec : specs) {
            if (allowed.contains(spec.name())) {
                tools.put(spec.name(), new GuardedTool<>(spec, mapper, validator, metrics,
                        props.guardrails().maxToolCallsPerRun()));
            } else {
                log.info("Tool {} is not in jobagent.guardrails.allowed-tools: disabled", spec.name());
            }
        }
        log.info("Tools enabled: {}", tools.keySet());
    }

    public List<ToolCallback> callbacks() {
        return List.copyOf(tools.values());
    }

    public List<String> names() {
        return List.copyOf(tools.keySet());
    }

    public boolean isWriteTool(String name) {
        GuardedTool<?> tool = tools.get(name);
        return tool != null && tool.writes();
    }

    /** Runs a tool call the model asked for, or explains why it can't. */
    public String execute(String name, String arguments, AgentRunContext run) {
        GuardedTool<?> tool = tools.get(name);
        if (tool == null) {
            metrics.guardrail("tool_not_allowed");
            metrics.toolCall(String.valueOf(name), "not_allowed", run.channel(), 0);
            String json = json(ToolOutput.Problem.refused("tool_not_allowed",
                    "There is no tool called '" + name + "'. Available tools: " + String.join(", ", tools.keySet())));
            run.record(new ToolStep(String.valueOf(name), arguments, "not_allowed", 0, json));
            return json;
        }
        return tool.call(arguments, run.toToolContext());
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
