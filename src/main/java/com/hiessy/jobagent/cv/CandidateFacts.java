package com.hiessy.jobagent.cv;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Facts the gate rules compare the CV against. Same shape as worker/cv-eval/truth.json, so that file
 * can be used as it is. It holds personal data: keep the real one out of git.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CandidateFacts(
        @JsonProperty("name") String name,
        @JsonProperty("email") String email,
        @JsonProperty("phone_digits") String phoneDigits,
        @JsonProperty("current_employer") String currentEmployer,
        @JsonProperty("employers") List<String> employers,
        @JsonProperty("name_variants") List<String> nameVariants) {

    public CandidateFacts {
        employers = employers == null ? List.of() : List.copyOf(employers);
        nameVariants = nameVariants == null ? List.of() : List.copyOf(nameVariants);
    }
}
