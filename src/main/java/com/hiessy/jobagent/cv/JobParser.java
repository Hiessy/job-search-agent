package com.hiessy.jobagent.cv;

import static com.hiessy.jobagent.cv.PyText.PY;
import static com.hiessy.jobagent.cv.PyText.PY_I;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a posting into requirements (core.py {@code parse_job}). Structured files with
 * {@code Title:} and {@code Required:} lines are read as they are; free text is mapped onto the
 * keyword vocabulary, split into required and nice-to-have parts by its headings.
 */
public final class JobParser {

    private static final Pattern NICE_HEAD = Pattern.compile(
            "nice to have|nice-to-have|bonus|plus|preferred|desirable|deseable|valorable|suma|would be great|ideally", PY_I);
    private static final Pattern REQ_HEAD = Pattern.compile(
            "requirements|required|must have|qualifications|what you bring|requisitos|excluyentes|you have|skills", PY_I);
    private static final Pattern LEAD_REQ = Pattern.compile(
            "(\\d{1,2})(?:\\+| or more)?\\s*(?:years|yrs|a[ñn]os)[^.\\n\\d]{0,70}?"
                    + "(?:lead|leading|managing|manag|leadership|liderando|liderazgo|personas a cargo)", PY_I);
    static final Pattern YEARS = Pattern.compile("(\\d{1,2})\\+?\\s*(?:years|yrs|años|anos)", PY);
    private static final Pattern LINKEDIN_AT = Pattern.compile("\\s+at\\s+.*$", PY);
    private static final Map<String, Pattern> FIELDS = new ConcurrentHashMap<>();

    private final RuleSet rules;
    private final Terms terms;

    public JobParser(RuleSet rules) {
        this.rules = rules;
        this.terms = new Terms(rules);
    }

    public JobRequirements parse(String text) {
        if (!field(text, "Required").isEmpty()) {
            return new JobRequirements(field(text, "Title"), intField(text, "Min years"), intField(text, "Min lead years"),
                    items(text, "Knockout"), items(text, "Required"), items(text, "Nice to have"), text);
        }

        List<String> lines = PyText.splitLines(text).stream().map(PyText::strip).filter(s -> !s.isEmpty()).toList();
        String title = lines.isEmpty() ? "" : PyText.head(lines.get(0), 120);
        if (title.contains("| LinkedIn")) { // "Tech Lead at Company — Place | LinkedIn"
            title = LINKEDIN_AT.matcher(title).replaceAll("");
        }
        List<String> reqTxt = new ArrayList<>();
        List<String> niceTxt = new ArrayList<>();
        boolean nice = false;
        for (String ln : lines.subList(Math.min(1, lines.size()), lines.size())) {
            boolean shortLine = PyText.len(ln) < 80;
            if (shortLine && NICE_HEAD.matcher(ln).find() && !REQ_HEAD.matcher(ln).find()) {
                nice = true;
            } else if (shortLine && REQ_HEAD.matcher(ln).find()) {
                nice = false;
            }
            (nice ? niceTxt : reqTxt).add(ln);
            if (nice && NICE_HEAD.matcher(ln).find() && !shortLine) {
                niceTxt.add(ln);
            }
        }
        String r = PyText.norm(String.join("\n", reqTxt));
        String n = PyText.norm(String.join("\n", niceTxt));
        List<String> required = rules.vocab().keySet().stream().filter(k -> terms.has(k, r)).toList();
        List<String> niceToHave = rules.vocab().keySet().stream()
                .filter(k -> terms.has(k, n) && !required.contains(k)).toList();
        String t = PyText.norm(text);
        String normTitle = PyText.norm(title);
        List<String> knockout = new ArrayList<>(rules.vocab().keySet().stream().filter(k -> terms.has(k, normTitle)).toList());
        if (knockout.isEmpty()) {
            knockout.addAll(required.subList(0, Math.min(2, required.size())));
        }
        if (t.contains("english") || t.contains("inglés") || t.contains("ingles")) {
            knockout.add("English");
        }
        List<Integer> lead = PyText.findAll(LEAD_REQ, PyText.lower(text)).stream()
                .map(Integer::parseInt).filter(y -> y < 15).toList();
        List<Integer> years = PyText.findAll(YEARS, t).stream()
                .map(Integer::parseInt).filter(y -> y < 13 && !lead.contains(y)).toList();
        return new JobRequirements(title, years.isEmpty() ? 0 : Collections.max(years),
                lead.isEmpty() ? 0 : Collections.max(lead), List.copyOf(knockout), required, niceToHave, text);
    }

    private static String field(String text, String label) {
        Pattern p = FIELDS.computeIfAbsent(label, l -> Pattern.compile("^" + l + ":\\s*(.+)$", PY | Pattern.MULTILINE));
        Matcher m = p.matcher(text);
        return m.find() ? PyText.strip(m.group(1)) : "";
    }

    private static int intField(String text, String label) {
        String v = field(text, label);
        return v.matches("\\d+") ? Integer.parseInt(v) : 0;
    }

    private static List<String> items(String text, String label) {
        return Arrays.stream(field(text, label).split(",")).map(PyText::strip).filter(s -> !s.isEmpty()).toList();
    }
}
