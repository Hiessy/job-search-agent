package com.hiessy.jobagent.posting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * Reads a posting page as text. Prefers the schema.org JobPosting block that most job boards embed
 * (title, hiring organisation, description); falls back to the visible text without navigation,
 * scripts and footers. Same approach as core.py's html_to_text.
 */
public final class HtmlToText {

    /** What the page says about the job. {@code company} may be null. */
    public record Extracted(String title, String company, String text) {
    }

    private static final Set<String> BLOCK = Set.of("p", "div", "li", "ul", "ol", "br", "tr", "section", "article",
            "h1", "h2", "h3", "h4", "h5", "h6", "header", "footer", "table", "dd", "dt", "blockquote", "pre");
    /** "Rocbird hiring Senior AI Engineer in Argentina | LinkedIn" */
    private static final Pattern LINKEDIN_HIRING = Pattern.compile("^(.+?) hiring (.+?) in .+$");
    /** "Senior Java Engineer at Acme — Remote | LinkedIn" */
    private static final Pattern ROLE_AT = Pattern.compile("^(.+?) at (.+?)(?:\\s+[—|-]\\s+.*)?(?:\\s*\\|.*)?$");

    private final ObjectMapper mapper;

    public HtmlToText(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Extracted extract(String html) {
        Document doc = Jsoup.parse(html);
        for (Element script : doc.select("script[type=application/ld+json]")) {
            Extracted fromLd = fromJsonLd(script.data());
            if (fromLd != null) {
                return fromLd;
            }
        }
        String pageTitle = doc.title().strip();
        doc.select("script, style, noscript, svg, nav, footer, header, template").remove();
        String body = toText(doc.body() == null ? doc : doc.body());
        String[] titleAndCompany = splitTitle(pageTitle);
        String text = pageTitle.isEmpty() ? body : pageTitle + "\n" + body;
        return new Extracted(titleAndCompany[0], titleAndCompany[1], text);
    }

    private Extracted fromJsonLd(String json) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
        List<JsonNode> items = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(items::add);
        } else if (root.has("@graph")) {
            root.get("@graph").forEach(items::add);
        } else {
            items.add(root);
        }
        for (JsonNode it : items) {
            if (!isJobPosting(it.path("@type"))) {
                continue;
            }
            String title = it.path("title").asText("").strip();
            String desc = it.path("description").asText("");
            String descText = desc.contains("<") ? toText(Jsoup.parseBodyFragment(desc).body()) : desc.strip();
            JsonNode org = it.path("hiringOrganization");
            String company = org.isTextual() ? org.asText() : org.path("name").asText(null);
            return new Extracted(title, blankToNull(company), title + "\n" + descText);
        }
        return null;
    }

    private static boolean isJobPosting(JsonNode type) {
        if (type.isArray()) {
            for (JsonNode t : type) {
                if ("JobPosting".equals(t.asText())) {
                    return true;
                }
            }
            return false;
        }
        return "JobPosting".equals(type.asText());
    }

    /** Text with one line per block element, blank lines dropped. */
    static String toText(Node root) {
        StringBuilder sb = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) {
                    sb.append(t.text());
                } else if (node instanceof Element e && e.normalName().equals("br")) {
                    sb.append('\n');
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element e && BLOCK.contains(e.normalName())) {
                    sb.append('\n');
                }
            }
        }, root);
        List<String> lines = new ArrayList<>();
        for (String ln : sb.toString().split("\n")) {
            String s = ln.replace(' ', ' ').strip().replaceAll("\\s{2,}", " ");
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        return String.join("\n", lines);
    }

    /** Best effort: {title, company} from a page title. */
    static String[] splitTitle(String pageTitle) {
        Matcher m = LINKEDIN_HIRING.matcher(pageTitle.replaceAll("\\s*\\|\\s*LinkedIn.*$", ""));
        if (m.matches()) {
            return new String[] {m.group(2).strip(), m.group(1).strip()};
        }
        m = ROLE_AT.matcher(pageTitle);
        if (m.matches()) {
            return new String[] {m.group(1).strip(), m.group(2).strip()};
        }
        return new String[] {pageTitle.replaceAll("\\s*\\|.*$", "").strip(), null};
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
