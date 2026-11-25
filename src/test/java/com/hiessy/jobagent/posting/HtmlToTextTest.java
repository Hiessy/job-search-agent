package com.hiessy.jobagent.posting;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HtmlToTextTest {

    private final HtmlToText html = new HtmlToText(new ObjectMapper());

    @Test
    void prefersTheJobPostingJsonLd() {
        String page = """
                <html><head><title>Careers</title>
                <script type="application/ld+json">
                {"@context":"https://schema.org","@graph":[{"@type":"Organization","name":"x"},
                 {"@type":"JobPosting","title":"Senior Java Engineer","hiringOrganization":{"@type":"Organization","name":"Acme"},
                  "description":"<p>We need <b>Java</b> and Spring Boot.</p><ul><li>5+ years</li><li>Kafka</li></ul>"}]}
                </script></head><body><nav>Menu</nav><p>Cookie banner</p></body></html>""";

        HtmlToText.Extracted ex = html.extract(page);

        assertThat(ex.title()).isEqualTo("Senior Java Engineer");
        assertThat(ex.company()).isEqualTo("Acme");
        assertThat(ex.text()).isEqualTo("Senior Java Engineer\nWe need Java and Spring Boot.\n5+ years\nKafka");
    }

    @Test
    void fallsBackToVisibleTextWithoutNavigationAndScripts() {
        String page = """
                <html><head><title>Rocbird hiring Senior AI Engineer in Argentina | LinkedIn</title>
                <style>.x{}</style></head>
                <body><header>Sign in</header><nav>Jobs</nav>
                <div><h1>Senior AI Engineer</h1><p>Build agents with&nbsp;tool calling.</p><br>MCP and evals.</div>
                <script>track()</script><footer>Legal</footer></body></html>""";

        HtmlToText.Extracted ex = html.extract(page);

        assertThat(ex.title()).isEqualTo("Senior AI Engineer");
        assertThat(ex.company()).isEqualTo("Rocbird");
        assertThat(ex.text()).doesNotContain("Sign in", "Jobs\n", "track()", "Legal", ".x{}")
                .contains("Build agents with tool calling.\nMCP and evals.");
    }

    @Test
    void splitsRoleAtCompanyTitles() {
        assertThat(HtmlToText.splitTitle("Senior Java Backend Engineer at Acme Freight — Remote LATAM | LinkedIn"))
                .containsExactly("Senior Java Backend Engineer", "Acme Freight");
        assertThat(HtmlToText.splitTitle("Tech Lead | Careers")).containsExactly("Tech Lead", null);
    }

    @Test
    void plainTextUsesTitleAndSourceLines() {
        HtmlToText.Extracted ex = PostingService.plain("""
                Title: Tech Lead - Real Time Payments
                Required: Java, payments
                Source: https://www.linkedin.com/jobs/view/1/ (Pomelo, remote Buenos Aires, LinkedIn)
                """);
        assertThat(ex.title()).isEqualTo("Tech Lead - Real Time Payments");
        assertThat(ex.company()).isEqualTo("Pomelo");
    }
}
