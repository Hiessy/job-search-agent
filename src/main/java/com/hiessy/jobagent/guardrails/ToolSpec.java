package com.hiessy.jobagent.guardrails;

import java.util.function.BiFunction;

/**
 * One tool: its name and description for the model, the input record (its JSON schema and Jakarta
 * constraints come from that class), whether it writes, and the handler.
 *
 * @param writes true for tools that change state or produce something meant to be sent; these
 *               refuse once the run is tainted
 */
public record ToolSpec<I>(
        String name,
        String description,
        Class<I> inputType,
        boolean writes,
        BiFunction<I, AgentRunContext, ToolOutput> handler) {
}
