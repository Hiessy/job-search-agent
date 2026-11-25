package com.hiessy.jobagent.guardrails;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.observability.AgentMetrics;
import com.hiessy.jobagent.tools.ToolInputs;
import com.hiessy.jobagent.tools.ToolResults;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuardedToolTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private GuardedTool<ToolInputs.ScoreCv> scoreTool(int budget) {
        ToolSpec<ToolInputs.ScoreCv> spec = new ToolSpec<>("score_cv", "Score", ToolInputs.ScoreCv.class, false,
                (in, run) -> new ToolResults.Scored("OK", "P1", "t", "c", "WEAK", 70, "Medium", List.of(), List.of(),
                        List.of(), List.of(), List.of(), null));
        return new GuardedTool<>(spec, mapper, Validation.buildDefaultValidatorFactory().getValidator(),
                new AgentMetrics(registry), budget);
    }

    @Test
    void schemaUsesTheJsonNamesAndRequiredFlags() throws Exception {
        JsonNode schema = mapper.readTree(scoreTool(5).getToolDefinition().inputSchema());
        assertThat(schema.path("properties").has("job_text")).isTrue();
        assertThat(schema.path("required").toString()).contains("job_text");

        ToolSpec<ToolInputs.SaveApplication> save = new ToolSpec<>("save_application", "Save",
                ToolInputs.SaveApplication.class, true, (in, run) -> null);
        JsonNode saveSchema = mapper.readTree(new GuardedTool<>(save, mapper,
                Validation.buildDefaultValidatorFactory().getValidator(), new AgentMetrics(registry), 5)
                .getToolDefinition().inputSchema());
        assertThat(saveSchema.path("required")).extracting(JsonNode::asText).containsExactly("posting_id");
    }

    @Test
    void validationErrorsUseTheArgumentNames() {
        String out = scoreTool(5).call("{\"job_text\":\"\"}");
        assertThat(out).contains("INVALID_INPUT").contains("job_text: must not be blank");
        assertThat(registry.counter("jobagent.tool.failures", "tool", "score_cv", "reason", "invalid_input").count()).isEqualTo(1);
    }

    @Test
    void brokenJsonIsAnInputErrorNotAnException() {
        assertThat(scoreTool(5).call("not json")).contains("INVALID_INPUT");
    }

    @Test
    void callsWithoutARunAreCountedAsMcp() {
        assertThat(scoreTool(5).call("{\"job_text\":\"P1\"}")).contains("\"verdict\":\"WEAK\"");
        assertThat(registry.find("jobagent.tool.latency").tags("tool", "score_cv", "channel", "mcp", "outcome", "ok")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void enforcesTheToolBudgetPerRun() {
        GuardedTool<ToolInputs.ScoreCv> tool = scoreTool(2);
        AgentRunContext run = AgentRunContext.forAgent();
        tool.call("{\"job_text\":\"P1\"}", run.toToolContext());
        tool.call("{\"job_text\":\"P1\"}", run.toToolContext());
        String third = tool.call("{\"job_text\":\"P1\"}", run.toToolContext());
        assertThat(third).contains("tool_budget");
        assertThat(run.tainted()).isFalse();
    }
}
