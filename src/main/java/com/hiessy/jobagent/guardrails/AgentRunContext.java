package com.hiessy.jobagent.guardrails;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.ai.chat.model.ToolContext;

/**
 * State of one agent run, shared by its tool calls: the trace, the approvals it asked for, the tool
 * budget, and the taint flag. Once a run has read a posting that tried to instruct the agent, the
 * run is tainted and every write tool refuses for the rest of it.
 *
 * <p>MCP calls have no run around them, so each one gets a detached context of its own.
 */
public final class AgentRunContext {

    public static final String KEY = "jobagent.run";

    /** One tool call as it happened. {@code result} is cut to 500 characters. */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ToolStep(String tool, String arguments, String outcome, long latencyMs, String result) {
    }

    private final String runId;
    private final String channel;
    private final List<ToolStep> steps = new CopyOnWriteArrayList<>();
    private final List<Long> approvals = new CopyOnWriteArrayList<>();
    private final AtomicInteger toolCalls = new AtomicInteger();
    private volatile String taintReason;
    private volatile String lastVerdict;

    private AgentRunContext(String runId, String channel) {
        this.runId = runId;
        this.channel = channel;
    }

    public static AgentRunContext forAgent() {
        return new AgentRunContext(UUID.randomUUID().toString(), "agent");
    }

    public static AgentRunContext from(ToolContext toolContext) {
        if (toolContext != null && toolContext.getContext().get(KEY) instanceof AgentRunContext run) {
            return run;
        }
        return new AgentRunContext(UUID.randomUUID().toString(), "mcp");
    }

    public ToolContext toToolContext() {
        return new ToolContext(java.util.Map.of(KEY, this));
    }

    public String runId() {
        return runId;
    }

    /** "agent" for the agent loop, "mcp" for calls from MCP clients. */
    public String channel() {
        return channel;
    }

    public void record(ToolStep step) {
        steps.add(step);
    }

    public List<ToolStep> steps() {
        return List.copyOf(steps);
    }

    public int nextToolCall() {
        return toolCalls.incrementAndGet();
    }

    public void taint(String reason) {
        this.taintReason = reason;
    }

    public boolean tainted() {
        return taintReason != null;
    }

    public String taintReason() {
        return taintReason;
    }

    public void addApproval(long id) {
        approvals.add(id);
    }

    public List<Long> approvals() {
        return List.copyOf(approvals);
    }

    public void verdict(String verdict) {
        this.lastVerdict = verdict;
    }

    /** The last verdict score_cv returned in this run, or null. */
    public String verdict() {
        return lastVerdict;
    }
}
