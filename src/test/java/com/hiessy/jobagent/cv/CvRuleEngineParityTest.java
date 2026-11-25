package com.hiessy.jobagent.cv;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The Java engine must give exactly what worker/cv-eval/core.py gives: verdict, score, every rule's
 * value and wording, quality checks, confirm items and keyword lists. The expected values were
 * recorded from Python with tools/parity_fixture.py (a sample CV and a broken one, 20 postings).
 * {@code -Dparity.local=target/parity-local/expected.json} also checks your own CV (see that script).
 */
class CvRuleEngineParityTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path POSTINGS = Path.of("src", "test", "resources", "postings");
    /** core.py ends "Present" roles in 2026. */
    private static final CvRuleEngine ENGINE = new CvRuleEngine(RuleSet.fromClasspath(MAPPER), 2026);

    static Stream<Arguments> cases() throws IOException {
        return load(Path.of("src", "test", "resources", "parity", "expected.json"));
    }

    static Stream<Arguments> localCases() throws IOException {
        return load(Path.of(System.getProperty("parity.local")));
    }

    private static Stream<Arguments> load(Path fixture) throws IOException {
        List<Arguments> out = new ArrayList<>();
        MAPPER.readTree(fixture.toFile()).forEach(c -> out.add(Arguments.of(c.get("cv").asText(), c.get("posting").asText(), c)));
        return out.stream();
    }

    @ParameterizedTest(name = "{0} CV vs {1}")
    @MethodSource("cases")
    void matchesPython(String cv, String posting, JsonNode expectedCase) throws IOException {
        check(posting, expectedCase);
    }

    @ParameterizedTest(name = "{0} CV vs {1}")
    @MethodSource("localCases")
    @EnabledIfSystemProperty(named = "parity.local", matches = ".+")
    void matchesPythonOnYourCv(String cv, String posting, JsonNode expectedCase) throws IOException {
        check(posting, expectedCase);
    }

    private static void check(String posting, JsonNode expectedCase) throws IOException {
        CandidateFacts facts = MAPPER.readValue(Path.of(expectedCase.get("facts_path").asText()).toFile(), CandidateFacts.class);
        String cvText = read(Path.of(expectedCase.get("cv_path").asText()));

        Evaluation actual = ENGINE.evaluate(cvText, facts, read(POSTINGS.resolve(posting)));

        ObjectNode expected = expectedCase.deepCopy();
        expected.remove(List.of("cv", "posting", "cv_path", "facts_path"));
        expected.set("rating", expected.remove("label"));
        assertThat(canonical(MAPPER.valueToTree(actual))).isEqualTo(canonical(expected));
    }

    private static String canonical(JsonNode node) throws IOException {
        return MAPPER.writeValueAsString(MAPPER.convertValue(node, Object.class));
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }
}
