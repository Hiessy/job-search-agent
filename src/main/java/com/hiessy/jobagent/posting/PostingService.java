package com.hiessy.jobagent.posting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.config.JobAgentProperties;
import com.hiessy.jobagent.guardrails.PromptInjectionDetector;
import com.hiessy.jobagent.guardrails.ToolOutput;
import com.hiessy.jobagent.posting.PostingStore.Posting;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Reads postings for the tools: fetches or takes pasted text, refuses text that tries to instruct the
 * agent, and stores the rest under a posting_id. The model only ever sees a short preview.
 */
@Service
public class PostingService {

    private static final int PREVIEW_CHARS = 800;
    private static final int MIN_CHARS = 300;
    private static final Pattern TITLE_FIELD = Pattern.compile("^Title:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern SOURCE_COMPANY = Pattern.compile("^Source:\\s*\\S+\\s*\\(([^,)]+)", Pattern.MULTILINE);

    /** Either a posting or the reason there isn't one. */
    public record Resolved(Posting posting, ToolOutput problem) {
    }

    private final PostingSource source;
    private final UrlGuard guard;
    private final PostingStore store;
    private final PromptInjectionDetector detector;
    private final HtmlToText html;
    private final int maxChars;

    public PostingService(PostingSource source, UrlGuard guard, PostingStore store, PromptInjectionDetector detector,
                          ObjectMapper mapper, JobAgentProperties props) {
        this.source = source;
        this.guard = guard;
        this.store = store;
        this.detector = detector;
        this.html = new HtmlToText(mapper);
        this.maxChars = props.fetch().maxChars();
    }

    public Resolved fetch(String url) {
        URI uri = guard.check(url);
        PostingSource.Page page;
        try {
            page = source.load(uri);
        } catch (IOException e) {
            return new Resolved(null, ToolOutput.Problem.error("Could not read " + uri.getHost() + ": " + e.getMessage()
                    + ". Ask the user to paste the posting text instead."));
        }
        HtmlToText.Extracted ex = page.contentType().toLowerCase().contains("html")
                ? html.extract(page.body()) : plain(page.body());
        String text = cut(ex.text(), maxChars);
        if (text.strip().length() < MIN_CHARS) {
            return new Resolved(null, ToolOutput.Problem.error("The page has almost no text (it probably needs "
                    + "JavaScript or a login). Ask the user to paste the posting text instead."));
        }
        PromptInjectionDetector.Finding finding = detector.scan(text);
        if (finding.flagged()) {
            return new Resolved(null, ToolOutput.Problem.injection("The posting contains instructions aimed at AI tools: "
                    + finding.summary() + ". It was not read or scored."));
        }
        return new Resolved(store.add(uri.toString(), ex.title(), ex.company(), text), null);
    }

    public static String preview(Posting p) {
        return cut(p.text(), PREVIEW_CHARS);
    }

    /** A posting_id from an earlier call, or posting text the user pasted. */
    public Resolved resolve(String jobTextOrId) {
        if (PostingStore.isId(jobTextOrId)) {
            return store.get(jobTextOrId)
                    .map(p -> new Resolved(p, null))
                    .orElseGet(() -> new Resolved(null, ToolOutput.Problem.notFound("Unknown posting_id " + jobTextOrId
                            + ". Call fetch_job_posting with the URL, or pass the posting text.")));
        }
        String text = cut(jobTextOrId, maxChars);
        if (text.strip().length() < 100) {
            return new Resolved(null, ToolOutput.Problem.invalid(java.util.List.of(
                    "job_text: pass a posting_id like P1 or the full posting text (at least 100 characters)")));
        }
        PromptInjectionDetector.Finding finding = detector.scan(text);
        if (finding.flagged()) {
            return new Resolved(null, ToolOutput.Problem.injection(
                    "The posting text contains instructions aimed at AI tools: " + finding.summary() + ". It was not scored."));
        }
        HtmlToText.Extracted ex = plain(text);
        return new Resolved(store.add(null, ex.title(), ex.company(), text), null);
    }

    public java.util.Optional<Posting> get(String id) {
        return store.get(id);
    }

    /** Plain text: a "Title:" line or the first line is the title; "Source: url (Company, ...)" gives the company. */
    static HtmlToText.Extracted plain(String text) {
        Matcher t = TITLE_FIELD.matcher(text);
        String firstLine = Arrays.stream(text.split("\n")).map(String::strip).filter(s -> !s.isEmpty()).findFirst().orElse("");
        String[] split = HtmlToText.splitTitle(t.find() ? t.group(1).strip() : firstLine);
        Matcher c = SOURCE_COMPANY.matcher(text);
        String company = c.find() ? c.group(1).strip() : split[1];
        return new HtmlToText.Extracted(cut(split[0], 200), company, text);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
