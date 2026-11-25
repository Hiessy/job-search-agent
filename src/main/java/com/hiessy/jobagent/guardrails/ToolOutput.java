package com.hiessy.jobagent.guardrails;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Every tool returns JSON with a {@code status}: OK, PENDING_APPROVAL, REFUSED, INVALID_INPUT,
 * NOT_FOUND or ERROR. A prompt-injection refusal taints the run (see {@link AgentRunContext}).
 */
public interface ToolOutput {

    String OK = "OK";
    String PENDING_APPROVAL = "PENDING_APPROVAL";
    String REFUSED = "REFUSED";
    String INVALID_INPUT = "INVALID_INPUT";
    String NOT_FOUND = "NOT_FOUND";
    String ERROR = "ERROR";

    String status();

    /** Generic failure: refusals, bad input, errors. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record Problem(String status, String rule, String reason, List<String> errors) implements ToolOutput {

        public static final String PROMPT_INJECTION = "prompt_injection";

        /** Untrusted text tried to instruct the agent. Taints the run. */
        public static Problem injection(String reason) {
            return new Problem(REFUSED, PROMPT_INJECTION, reason, List.of());
        }

        public static Problem refused(String rule, String reason) {
            return new Problem(REFUSED, rule, reason, List.of());
        }

        public static Problem invalid(List<String> errors) {
            return new Problem(INVALID_INPUT, null, "Fix the arguments and call the tool again.", errors);
        }

        public static Problem notFound(String reason) {
            return new Problem(NOT_FOUND, null, reason, List.of());
        }

        public static Problem error(String reason) {
            return new Problem(ERROR, null, reason, List.of());
        }

        @JsonIgnore
        public boolean isInjection() {
            return PROMPT_INJECTION.equals(rule);
        }
    }
}
