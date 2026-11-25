package com.hiessy.jobagent.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hiessy.jobagent.guardrails.AgentRunContext.ToolStep;
import java.util.List;

/**
 * What one agent run did: the answer, every tool call in order, approval requests created, tokens.
 *
 * @param outcome         ANSWERED, REFUSED (prompt injection), STEP_LIMIT or MODEL_ERROR
 * @param reportedVerdict the verdict the model wrote on its VERDICT line, if any
 * @param toolVerdict     the verdict score_cv actually returned, if it ran
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentRun(
        String conversationId,
        String runId,
        String outcome,
        String answer,
        String reportedVerdict,
        String toolVerdict,
        List<ToolStep> steps,
        List<Long> pendingApprovals,
        int promptTokens,
        int completionTokens,
        long durationMs,
        String model) {

    @JsonProperty("tool_sequence")
    public List<String> toolSequence() {
        return steps.stream().map(ToolStep::tool).toList();
    }
}
