package com.hiessy.jobagent.cv;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/** What a posting asks for, as read by {@link JobParser}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record JobRequirements(
        String title,
        int minYears,
        int minLeadYears,
        List<String> knockout,
        List<String> required,
        List<String> nice,
        @JsonIgnore String body) {
}
