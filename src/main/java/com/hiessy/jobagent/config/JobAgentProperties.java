package com.hiessy.jobagent.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Everything under {@code jobagent.*} in application.yml. */
@ConfigurationProperties("jobagent")
public record JobAgentProperties(Cv cv, Agent agent, Guardrails guardrails, Fetch fetch) {

    /**
     * @param path       the CV as text, or a PDF (read with pdftotext -layout); {@code classpath:} works too
     * @param factsPath  candidate facts in worker/cv-eval/truth.json format
     * @param variants   optional tailored CVs by name, for compare_cv_variants
     * @param currentYear end year for roles marked "Present"; 0 means this year
     */
    public record Cv(
            @DefaultValue("classpath:samples/cv.sample.txt") String path,
            @DefaultValue("classpath:samples/candidate.sample.json") String factsPath,
            Map<String, String> variants,
            @DefaultValue("0") int currentYear,
            @DefaultValue("pdftotext") String pdftotext) {

        public Cv {
            variants = variants == null ? Map.of() : Map.copyOf(variants);
        }
    }

    /**
     * @param maxSteps           model turns per run before the loop gives up
     * @param maxHistoryMessages messages kept per conversation
     */
    public record Agent(@DefaultValue("8") int maxSteps, @DefaultValue("40") int maxHistoryMessages) {
    }

    /**
     * @param allowedTools       the only tools the model and MCP clients can call
     * @param maxToolCallsPerRun budget across all steps of one run
     */
    public record Guardrails(List<String> allowedTools, @DefaultValue("10") int maxToolCallsPerRun) {

        public Guardrails {
            allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        }
    }

    /**
     * @param allowPrivateHosts only for tests: lets fetch_job_posting read localhost
     */
    public record Fetch(
            @DefaultValue("20s") Duration timeout,
            @DefaultValue("3000000") int maxBytes,
            @DefaultValue("20000") int maxChars,
            @DefaultValue("false") boolean allowPrivateHosts,
            @DefaultValue("Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130 Safari/537.36") String userAgent) {
    }
}
