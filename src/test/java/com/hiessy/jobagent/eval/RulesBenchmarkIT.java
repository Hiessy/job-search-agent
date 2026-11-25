package com.hiessy.jobagent.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.config.JobAgentProperties;
import com.hiessy.jobagent.cv.CvProfile;
import com.hiessy.jobagent.cv.CvRuleEngine;
import com.hiessy.jobagent.cv.Evaluation;
import com.hiessy.jobagent.cv.RuleSet;
import com.hiessy.jobagent.support.FixturePostingSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * How far score_cv's rule score is from an outside screener's scores for the same CV and postings:
 * mean gap and Spearman rank agreement. Needs your real CV, because the benchmark scored that one,
 * so it only runs when asked:
 *
 * <pre>
 * mvn -Peval verify -Dit.test=RulesBenchmarkIT -Dbenchmark=../../worker/cv-eval/benchmarks/chatgpt-2026-11-24.json \
 *     -Dcv=../../worker/ats-cv/CV_-_Martin_Diaz_ATS.pdf -Dfacts=../../worker/cv-eval/truth.json
 * </pre>
 *
 * The report (scores only, no CV text) goes to target/eval/benchmark.md.
 */
@EnabledIfSystemProperty(named = "benchmark", matches = ".+")
class RulesBenchmarkIT {

    @Test
    void compareWithBenchmark() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode bench = mapper.readTree(Path.of(System.getProperty("benchmark")).toFile());
        JobAgentProperties props = new JobAgentProperties(
                new JobAgentProperties.Cv("file:" + System.getProperty("cv"), "file:" + System.getProperty("facts"), Map.of(),
                        0, "pdftotext"),
                new JobAgentProperties.Agent(8, 40), new JobAgentProperties.Guardrails(List.of(), 10),
                new JobAgentProperties.Fetch(Duration.ofSeconds(5), 1000, 1000, false, "x"));
        CvProfile profile = new CvProfile(props, new DefaultResourceLoader(), mapper);
        CvRuleEngine engine = new CvRuleEngine(RuleSet.fromClasspath(mapper), java.time.Year.now().getValue());

        List<String> names = new ArrayList<>();
        List<Double> theirs = new ArrayList<>();
        List<Double> ours = new ArrayList<>();
        List<String> verdicts = new ArrayList<>();
        bench.path("scores").properties().forEach(e -> {
            String posting = FixturePostingSource.read(FixturePostingSource.DIR.resolve(e.getKey() + ".txt"));
            Evaluation ev = engine.evaluate(profile.primary().text(), profile.facts(), posting);
            names.add(e.getKey());
            theirs.add(e.getValue().asDouble());
            ours.add((double) ev.score());
            verdicts.add(ev.verdict());
        });
        double rho = Spearman.rho(ours.stream().mapToDouble(d -> d).toArray(), theirs.stream().mapToDouble(d -> d).toArray());
        double meanGap = 0;
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            double gap = ours.get(i) - theirs.get(i);
            meanGap += Math.abs(gap);
            rows.append(String.format(Locale.ROOT, "| %s | %.0f | %.0f | %+.0f | %s |%n", names.get(i), theirs.get(i),
                    ours.get(i), gap, verdicts.get(i)));
        }
        meanGap /= names.size();
        String report = String.format(Locale.ROOT, "## score_cv against %s (%s)%n%nSpearman rank agreement: %.2f. "
                        + "Mean absolute gap: %.1f points over %d postings.%n%n| Posting | Benchmark | score_cv | Gap | Verdict |%n"
                        + "|---|---|---|---|---|%n%s", Path.of(System.getProperty("benchmark")).getFileName(),
                bench.path("date").asText(), rho, meanGap, names.size(), rows);
        Files.createDirectories(Path.of("target", "eval"));
        Files.writeString(Path.of("target", "eval", "benchmark.md"), report, StandardCharsets.UTF_8);
        System.out.println(report);
        assertThat(rho).isBetween(-1.0, 1.0);
    }
}
