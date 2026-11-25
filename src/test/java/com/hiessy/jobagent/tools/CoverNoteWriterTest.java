package com.hiessy.jobagent.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.cv.CandidateFacts;
import com.hiessy.jobagent.cv.CvProfile;
import com.hiessy.jobagent.cv.CvRuleEngine;
import com.hiessy.jobagent.cv.Evaluation;
import com.hiessy.jobagent.cv.RuleSet;
import com.hiessy.jobagent.posting.PostingStore.Posting;
import com.hiessy.jobagent.support.FixturePostingSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoverNoteWriterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void namesOnlySkillsBackedByExperienceAndInventsNoNumbers() throws Exception {
        CandidateFacts facts = MAPPER.readValue(Path.of("src/main/resources/samples/candidate.sample.json").toFile(),
                CandidateFacts.class);
        String cv = Files.readString(Path.of("src/main/resources/samples/cv.sample.txt"));
        CvProfile profile = new CvProfile(facts, List.of(new CvProfile.CvDocument(CvProfile.PRIMARY, cv)));
        String posting = FixturePostingSource.read(FixturePostingSource.DIR.resolve("li-pomelo-tech-lead-payments.txt"));
        Evaluation e = new CvRuleEngine(RuleSet.fromClasspath(MAPPER), 2026).evaluate(cv, facts, posting);

        ToolResults.CoverNote note = new CoverNoteWriter()
                .draft(new Posting("P3", "https://x", "Tech Lead - Real Time Payments", "Pomelo", posting), e, profile, null);

        assertThat(note.note()).startsWith("Hello Pomelo team,").contains("Tech Lead - Real Time Payments").endsWith("Alex Rivera");
        assertThat(note.note().replaceAll("10\\+ years", "")).doesNotContainPattern("\\d");
        assertThat(note.groundedOn()).isNotEmpty()
                .allSatisfy(skill -> assertThat(e.keywords().missingRequired()).doesNotContain(skill));
        assertThat(note.questionsForUser()).first().asString().contains("won't make one up");
    }
}
