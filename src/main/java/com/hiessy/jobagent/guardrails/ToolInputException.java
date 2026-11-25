package com.hiessy.jobagent.guardrails;

/** A tool argument failed validation. The message goes back to the model so it can correct the call. */
public class ToolInputException extends RuntimeException {

    public ToolInputException(String message) {
        super(message);
    }
}
