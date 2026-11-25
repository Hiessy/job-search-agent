package com.hiessy.jobagent.tools;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.hiessy.jobagent.guardrails.ToolOutput;
import java.util.List;

/** What each tool returns to the model, as JSON. Short on purpose: small models have small contexts. */
public final class ToolResults {

    private ToolResults() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Fetched(String status, String postingId, String title, String company, int chars, String preview,
                          String note) implements ToolOutput {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Scored(String status, String postingId, String title, String company, String verdict, int score,
                         String rating, List<String> failedRules, List<String> qualityIssues, List<String> confirmWithUser,
                         List<String> missingRequired, List<String> skillsListOnly, String note) implements ToolOutput {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CoverNote(String status, String postingId, String note, List<String> groundedOn,
                            List<String> questionsForUser, String next) implements ToolOutput {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SaveRequested(String status, long approvalId, String summary, String message) implements ToolOutput {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ApplicationList(String status, int count, long pendingApprovals, List<Item> applications)
            implements ToolOutput {

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record Item(long id, String company, String role, String status, String verdict, Integer score,
                           String postingUrl, String createdAt) {
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record VariantComparison(String status, String postingId, String title, List<Variant> variants,
                                    String howToChoose) implements ToolOutput {

        @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
        public record Variant(String name, String verdict, int score, List<String> missingKnockouts,
                              List<String> missingRequired, String headline, String summary) {
        }
    }
}
