package com.hiessy.jobagent.cv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

/**
 * Result of scoring one CV against one posting. Mirrors the dict returned by core.evaluate().
 * {@code confirm} lists posting conditions a CV can't show (start date, time zone, visa...): the
 * agent asks the user about them instead of scoring them.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record Evaluation(
        JobRequirements job,
        List<GateCheck> gates,
        Map<String, RuleResult> match,
        List<QualityCheck> quality,
        List<String> confirm,
        String verdict,
        int score,
        String rating,
        Keywords keywords) {

    /** A check on the CV itself (G1 to G6). */
    public record GateCheck(String id, String label, boolean ok) {
    }

    /** A check of the CV against the posting (M1 to M8). {@code value} is null for pass/fail rules. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuleResult(String label, boolean ok, Integer value, String detail) {
    }

    /** What screeners reward beyond keywords (Q1 measurable results, Q2 leadership scope). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QualityCheck(String id, String label, boolean ok, Integer value, String detail) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Keywords(
            List<String> missingRequired,
            List<String> missingNice,
            List<String> skillsListOnly,
            List<String> headlineWords) {
    }
}
