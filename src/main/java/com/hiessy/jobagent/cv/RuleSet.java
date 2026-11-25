package com.hiessy.jobagent.cv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thresholds, weights, synonyms and the keyword vocabulary, read from {@code rules/rules.json} and
 * {@code rules/vocab.json}. Both files are copied from worker/cv-eval and must stay identical to it
 * (tools/parity_fixture.py checks this).
 */
public record RuleSet(
        List<String> requireSections,
        int maxGarbledChars,
        int requiredCoverageMin,
        int requiredCoverageStrong,
        int evidenceMin,
        int evidenceStrong,
        int recentJobs,
        int recencyMin,
        int titleAlignmentMin,
        int niceCoverageBonus,
        int impactBulletsMin,
        List<String> englishLevels,
        Map<String, List<String>> synonyms,
        Map<String, Integer> scoreWeights,
        List<Map.Entry<Integer, String>> ratingLabels,
        Map<String, List<String>> vocab) {

    public static RuleSet fromClasspath(ObjectMapper mapper) {
        JsonNode rules = read(mapper, "rules/rules.json");
        JsonNode vocab = read(mapper, "rules/vocab.json");
        JsonNode gates = rules.path("gates");
        JsonNode match = rules.path("match");
        List<Map.Entry<Integer, String>> labels = new ArrayList<>();
        rules.path("rating_labels").forEach(l -> labels.add(Map.entry(l.get(0).asInt(), l.get(1).asText())));
        Map<String, Integer> weights = new LinkedHashMap<>();
        rules.path("score_weights").properties().forEach(e -> weights.put(e.getKey(), e.getValue().asInt()));
        return new RuleSet(
                strings(gates.path("require_sections")),
                gates.path("max_garbled_chars").asInt(),
                match.path("required_coverage_min").asInt(),
                match.path("required_coverage_strong").asInt(),
                match.path("evidence_min").asInt(),
                match.path("evidence_strong").asInt(),
                match.path("recent_jobs").asInt(),
                match.path("recency_min").asInt(),
                match.path("title_alignment_min").asInt(),
                match.path("nice_coverage_bonus").asInt(),
                rules.path("quality").path("impact_bullets_min").asInt(),
                strings(rules.path("english_levels")),
                stringLists(rules.path("synonyms")),
                weights,
                labels,
                stringLists(vocab));
    }

    private static JsonNode read(ObjectMapper mapper, String path) {
        try (InputStream in = RuleSet.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + path);
            }
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Keeps JSON key order: the engine walks the vocabulary in file order, as Python does. */
    private static Map<String, List<String>> stringLists(JsonNode object) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        object.properties().forEach(e -> out.put(e.getKey(), strings(e.getValue())));
        return out;
    }

    int weight(String name) {
        return scoreWeights.getOrDefault(name, 0);
    }
}
