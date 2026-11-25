package com.hiessy.jobagent.tools;

import com.hiessy.jobagent.cv.CvProfile;
import com.hiessy.jobagent.cv.Evaluation;
import com.hiessy.jobagent.posting.PostingStore.Posting;
import com.hiessy.jobagent.tools.ToolResults.CoverNote;
import com.hiessy.jobagent.guardrails.ToolOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Drafts the note to the employer from a template, not from the model. It names only required
 * skills the rule engine found in the CV's experience bullets, and no numbers except the years the
 * CV states, so it can't invent experience or results. What it can't know, it asks the user.
 */
@Component
public class CoverNoteWriter {

    private static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\+?\\s*(?:years|yrs|años|anos)");
    private static final Map<String, String> DISPLAY = Map.ofEntries(Map.entry("java", "Java"),
            Map.entry("spring boot", "Spring Boot"), Map.entry("postgresql", "PostgreSQL"), Map.entry("kafka", "Kafka"),
            Map.entry("aws", "AWS"), Map.entry("gcp", "GCP"), Map.entry("azure", "Azure"), Map.entry("docker", "Docker"),
            Map.entry("kubernetes", "Kubernetes"), Map.entry("rest", "REST APIs"), Map.entry("ci/cd", "CI/CD"),
            Map.entry("sql", "SQL"), Map.entry("python", "Python"), Map.entry("typescript", "TypeScript"),
            Map.entry("react", "React"), Map.entry("llm", "LLMs"), Map.entry("mcp", "MCP"), Map.entry("rag", "RAG"),
            Map.entry("microservices", "microservices"), Map.entry("terraform", "Terraform"),
            Map.entry("github actions", "GitHub Actions"), Map.entry("junit", "JUnit"), Map.entry("mockito", "Mockito"),
            Map.entry("testcontainers", "Testcontainers"), Map.entry("hibernate", "Hibernate"), Map.entry("jpa", "JPA"),
            Map.entry("redis", "Redis"), Map.entry("mysql", "MySQL"), Map.entry("event-driven", "event-driven design"));

    public CoverNote draft(Posting posting, Evaluation e, CvProfile profile, String companyOverride) {
        String company = firstNonBlank(companyOverride, posting.company());
        String role = firstNonBlank(posting.title(), e.job().title(), "this role");
        List<String> found = e.job().required().stream()
                .filter(k -> !e.keywords().missingRequired().contains(k) && !e.keywords().skillsListOnly().contains(k))
                .filter(k -> !k.equalsIgnoreCase("english"))
                .toList();
        boolean versioned = found.stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).matches("java \\d+"));
        List<String> backed = found.stream()
                .filter(k -> !(versioned && k.equalsIgnoreCase("java")))
                .limit(5)
                .map(CoverNoteWriter::display)
                .toList();
        Matcher years = YEARS.matcher(profile.primary().text().toLowerCase(Locale.ROOT));

        StringBuilder note = new StringBuilder();
        note.append(company == null ? "Hello," : "Hello " + company + " team,").append("\n\n");
        note.append("I'm writing about the ").append(role).append(" role.");
        if (years.find()) {
            note.append(" I have ").append(years.group(1)).append("+ years of experience in software engineering");
            note.append(backed.isEmpty() ? "." : ", and my recent work covers " + join(backed) + ".");
        } else if (!backed.isEmpty()) {
            note.append(" My recent work covers ").append(join(backed)).append('.');
        }
        note.append("\n\nI'd welcome a short call to see whether my experience fits what your team needs.\n\n");
        note.append("Best regards,\n").append(profile.facts().name());

        List<String> questions = new ArrayList<>();
        questions.add("The draft has no results or numbers. Is there one you can back up (team size, users, %, volume) "
                + "that you want in it? I won't make one up.");
        e.quality().stream().filter(q -> q.id().equals("Q1") && !q.ok()).findFirst().ifPresent(q ->
                questions.add("Your CV is light on measurable results (" + q.detail() + "). Adding real ones to the CV helps more than the note."));
        Evaluation.RuleResult m1 = e.match().get("M1");
        if (!m1.ok()) {
            questions.add("The posting asks for skills the CV doesn't show (" + m1.detail() + "). Leave them out unless you have that experience.");
        }
        if (!e.confirm().isEmpty()) {
            questions.add("Confirm before sending: " + String.join(", ", e.confirm()) + ".");
        }
        if (company == null) {
            questions.add("Which company is this for? The posting doesn't say.");
        }
        return new CoverNote(ToolOutput.OK, posting.id(), note.toString(), backed, questions,
                "Show the draft and the questions to the user. They don't block other steps: if the user asked to save "
                        + "the application, call save_application with posting_id \"" + posting.id() + "\" now.");
    }

    private static String display(String term) {
        String t = term.toLowerCase(Locale.ROOT);
        return t.matches("java \\d+") ? "Java " + t.substring(5) : DISPLAY.getOrDefault(t, term);
    }

    private static String join(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.strip();
            }
        }
        return null;
    }
}
