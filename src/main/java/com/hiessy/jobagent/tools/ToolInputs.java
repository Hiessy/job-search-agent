package com.hiessy.jobagent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Tool arguments. Each record is both the JSON schema the model sees (names from
 * {@code @JsonProperty}, descriptions and required flags from {@code @ToolParam}) and the
 * constraints checked before the tool runs.
 */
public final class ToolInputs {

    private ToolInputs() {
    }

    static final String POSTING_ID = "^[Pp]\\d{1,6}$";
    static final String POSTING_ID_MESSAGE = "must be a posting_id like P1, as returned by fetch_job_posting or score_cv";

    public record FetchJobPosting(
            @ToolParam(description = "Full http(s) URL of the job posting page")
            @JsonProperty("url") @NotBlank @Size(max = 2048) String url) {
    }

    public record ScoreCv(
            @ToolParam(description = "The posting_id returned by fetch_job_posting (for example P1), "
                    + "or the full posting text if the user pasted it into the chat")
            @JsonProperty("job_text") @NotBlank @Size(max = 20000) String jobText) {
    }

    public record DraftCoverNote(
            @ToolParam(description = "posting_id of the posting the note is for (for example P1)")
            @JsonProperty("posting_id") @NotBlank @Pattern(regexp = POSTING_ID, message = POSTING_ID_MESSAGE) String postingId,
            @ToolParam(required = false, description = "Company name, only if the posting does not show it")
            @JsonProperty("company") @Size(max = 200) String company) {
    }

    public record SaveApplication(
            @ToolParam(description = "posting_id of the posting to track (for example P1)")
            @JsonProperty("posting_id") @NotBlank @Pattern(regexp = POSTING_ID, message = POSTING_ID_MESSAGE) String postingId,
            @ToolParam(required = false, description = "Company name, only if the posting does not show it")
            @JsonProperty("company") @Size(max = 200) String company,
            @ToolParam(required = false, description = "Short note from the user, for example why they apply")
            @JsonProperty("notes") @Size(max = 500) String notes) {
    }

    public record ListApplications(
            @ToolParam(required = false, description = "Only applications with this status: SAVED, APPLIED, "
                    + "INTERVIEW, OFFER, REJECTED or WITHDRAWN. Leave out for all")
            @JsonProperty("status")
            @Pattern(regexp = "(?i)^(SAVED|APPLIED|INTERVIEW|OFFER|REJECTED|WITHDRAWN)?$",
                    message = "must be one of SAVED, APPLIED, INTERVIEW, OFFER, REJECTED, WITHDRAWN") String status,
            @ToolParam(required = false, description = "How many to return, 1 to 50 (default 20)")
            @JsonProperty("limit") @Min(1) @Max(50) Integer limit) {
    }

    public record CompareCvVariants(
            @ToolParam(description = "posting_id of the posting (for example P1)")
            @JsonProperty("posting_id") @NotBlank @Pattern(regexp = POSTING_ID, message = POSTING_ID_MESSAGE) String postingId) {
    }
}
