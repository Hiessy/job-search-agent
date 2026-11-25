package com.hiessy.jobagent.cv;

import static com.hiessy.jobagent.cv.PyText.PY;
import static com.hiessy.jobagent.cv.PyText.PY_I;
import static com.hiessy.jobagent.cv.PyText.lower;
import static com.hiessy.jobagent.cv.PyText.norm;
import static com.hiessy.jobagent.cv.PyText.round;

import com.hiessy.jobagent.cv.Evaluation.GateCheck;
import com.hiessy.jobagent.cv.Evaluation.Keywords;
import com.hiessy.jobagent.cv.Evaluation.QualityCheck;
import com.hiessy.jobagent.cv.Evaluation.RuleResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java port of the ATS rule engine in worker/cv-eval/core.py (rules in its RULES.md). Deterministic:
 * the same CV and posting always give the same verdict and score, which is what lets the agent's
 * answers be checked. {@code CvRuleEngineParityTest} compares it with the Python output.
 */
public final class CvRuleEngine {

    public static final List<String> VERDICTS = List.of("STRONG", "ACCEPTABLE", "WEAK", "REJECT", "REJECT (fix CV)");

    private static final Set<String> STOP = Set.of("a", "an", "the", "of", "and", "for", "in", "with", "products", "remote",
            "latam", "sr", "ssr", "to", "at", "on", "hands", "only", "based", "arg", "uy", "argentina", "brazil", "peru",
            "colombia", "columbia", "trabajo", "remoto", "grupo", "quintoandar", "bilingual", "op");
    /** Title words that mean the same thing ("Technical Leader" = "Tech Lead", "Developer" = "Engineer"). */
    private static final Map<String, String> CANON = Map.ofEntries(Map.entry("technical", "tech"),
            Map.entry("leader", "lead"), Map.entry("leading", "lead"), Map.entry("lider", "lead"),
            Map.entry("engineering", "engineer"), Map.entry("engineers", "engineer"), Map.entry("developer", "engineer"),
            Map.entry("development", "engineer"), Map.entry("dev", "engineer"), Map.entry("programmer", "engineer"),
            Map.entry("software", "software"), Map.entry("senior", "senior"), Map.entry("sde", "engineer"));
    private static final Pattern LEAD_TITLE = Pattern.compile(
            "\\b(lead|leader|manager|head|principal|staff|supervisor|l[ií]der)\\b", PY_I);
    private static final Pattern DATE_RANGE = Pattern.compile(
            "\\b((?:19|20)\\d{2})\\s*[–-]\\s*((?:19|20)\\d{2}|present|current)", PY_I);
    private static final Pattern STATED_LEAD = Pattern.compile(
            "(\\d{1,2})\\+?\\s*years? (?:of )?(?:technical |team |engineering )?leadership", PY_I);
    private static final Pattern ENGLISH = Pattern.compile("english[^a-z]{0,3}\\(?([a-z0-9 /]{0,25})", PY);
    private static final Pattern GARBLED = Pattern.compile("\\(cid:\\d+\\)|[​ㅤ️]", PY);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\[[^\\]]{1,40}\\]|your-profile|your-user", PY);
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b", PY);
    private static final Pattern NON_DIGIT = Pattern.compile("\\D", PY);
    private static final Pattern WORD = Pattern.compile("[a-z]+");
    private static final String NUM_WORDS = "(?:\\d+|one|two|three|four|five|six|seven|eight|nine|ten|twelve|fifteen|twenty)";
    private static final Pattern IMPACT = Pattern.compile("\\d+(?:\\.\\d+)?\\s*(?:%|x\\b|k\\b|m\\b|ms\\b)|\\b" + NUM_WORDS
            + "[ -]?(?:\\+\\s*)?(?:[a-z]+ )?(?:person|people|engineers?|developers?|hours?|minutes?|seconds?|gb|tb"
            + "|microservices?|servers?|vulnerabilities|cases|tests|pipelines?|agents?|users?|customers?|clients?|services?"
            + "|teams?|records|requests|countries|markets|apps?|applications)\\b", PY_I);
    private static final Pattern TEAM_SIZE = Pattern.compile("\\b(?:team of|managing|managed|led|leading|supervis\\w*)\\s+"
            + "(?:a team of\\s+)?" + NUM_WORDS + "\\b|\\b" + NUM_WORDS + "[ -](?:person|people|engineer|developer)", PY_I);
    /** Posting conditions a CV can't show; the agent asks the user about them. */
    private static final List<Map.Entry<String, Pattern>> LOGISTICS = List.of(
            Map.entry("start date", Pattern.compile(
                    "\\b(start (?:date|in|within|full-time)|available to start|availability|inicio inmediato)\\b", PY)),
            Map.entry("time zone overlap", Pattern.compile("\\b(overlap|pacific|eastern|est\\b|pst\\b|cet\\b)", PY)),
            Map.entry("visa or travel", Pattern.compile("\\b(visa|travel|viajar)\\b", PY)),
            Map.entry("hybrid or on-site", Pattern.compile("\\b(hybrid|on-site|onsite|in-office|presencial|h[ií]brido)\\b", PY)),
            Map.entry("contract type", Pattern.compile("\\b(contractor|b2b|freelance|relaci[oó]n de dependencia)\\b", PY)),
            Map.entry("application deadline", Pattern.compile("\\b(deadline|accepted until)\\b", PY)));

    private final RuleSet rules;
    private final Terms terms;
    private final JobParser parser;
    private final int currentYear;

    /** @param currentYear end year for roles marked "Present" (core.py uses 2026) */
    public CvRuleEngine(RuleSet rules, int currentYear) {
        this.rules = rules;
        this.terms = new Terms(rules);
        this.parser = new JobParser(rules);
        this.currentYear = currentYear;
    }

    public JobRequirements parseJob(String jobText) {
        return parser.parse(jobText);
    }

    public Evaluation evaluate(String cvText, CandidateFacts facts, String jobText) {
        JobRequirements job = parser.parse(jobText);
        List<GateCheck> gates = gateChecks(cvText, facts);
        MatchOutcome match = matchListing(cvText, facts, job);
        List<QualityCheck> quality = qualityChecks(cvText, facts);
        String verdict = verdict(gates.stream().allMatch(GateCheck::ok), match.rules());
        int score = score(gates, match.rules(), quality);
        return new Evaluation(job, gates, match.rules(), quality, logistics(jobText), verdict, score, label(score),
                match.keywords());
    }

    // ---------- gates: the CV itself ----------

    List<GateCheck> gateChecks(String raw, CandidateFacts facts) {
        String t = norm(raw);
        List<String> lines = PyText.splitLines(raw);
        String head = norm(String.join("\n", lines.subList(0, Math.min(6, lines.size()))));
        List<GateCheck> checks = new ArrayList<>();
        checks.add(new GateCheck("G1", "Name on one line at the top",
                facts.nameVariants().stream().map(PyText::lower).anyMatch(head::contains)));
        checks.add(new GateCheck("G1", "Email readable", t.contains(nullToEmpty(facts.email()))));
        checks.add(new GateCheck("G1", "Phone readable",
                NON_DIGIT.matcher(raw).replaceAll("").contains(nullToEmpty(facts.phoneDigits()))));
        for (String sec : rules.requireSections()) {
            boolean ok = lines.stream().map(PyText::strip).anyMatch(s -> lower(s).contains(sec) && PyText.len(s) < 40);
            checks.add(new GateCheck("G2", "'" + capitalize(sec) + "' heading", ok));
        }
        int bad = (int) raw.chars().filter(c -> c == '�').count() + PyText.findAll(GARBLED, raw).size();
        checks.add(new GateCheck("G3", "Clean text (" + bad + " hidden/garbled chars)", bad <= rules.maxGarbledChars()));
        List<String> ph = PyText.findAll(PLACEHOLDER, raw);
        checks.add(new GateCheck("G4",
                "No placeholders" + (ph.isEmpty() ? "" : " (found " + PyText.repr(ph.subList(0, Math.min(4, ph.size()))) + ")"),
                ph.isEmpty()));
        Integer yrs = statedYears(t);
        checks.add(new GateCheck("G5", "States total years (" + (yrs == null || yrs == 0 ? "none" : yrs) + ")", yrs != null));
        List<String> undated = facts.employers().stream()
                .filter(e -> lines.stream().noneMatch(ln -> lower(ln).contains(lower(e)) && YEAR.matcher(ln).find()))
                .toList();
        checks.add(new GateCheck("G6", "Every job has its dates on the same line"
                + (undated.isEmpty() ? "" : " (not: " + String.join(", ", undated) + ")"), undated.isEmpty()));
        return checks;
    }

    // ---------- quality: what screeners reward beyond keywords ----------

    /** Bullet lines from Professional Experience (pdftotext -layout indents them). */
    static List<String> experienceBullets(String raw) {
        List<String> lines = new ArrayList<>();
        boolean inside = false;
        for (String ln : PyText.splitLines(raw)) {
            String stripped = PyText.strip(ln);
            String s = lower(stripped);
            if (s.equals("professional experience") || s.equals("experience") || s.equals("work experience")) {
                inside = true;
                continue;
            }
            if (inside && !s.isEmpty() && PyText.len(s) < 40 && PyText.isUpper(stripped)) {
                break;
            }
            if (inside && !s.isEmpty()) {
                lines.add(ln);
            }
        }
        List<String> chosen = lines.stream().filter(ln -> ln.startsWith("    ")).toList();
        if (chosen.isEmpty()) {
            chosen = lines.stream()
                    .filter(ln -> PyText.len(PyText.strip(ln)) > 50 && !lower(ln).contains("technologies:")).toList();
        }
        List<String> out = new ArrayList<>();
        for (String ln : chosen) {
            String s = PyText.strip(ln);
            if (!out.isEmpty() && Character.isLowerCase(s.codePointAt(0))) {
                out.set(out.size() - 1, out.get(out.size() - 1) + " " + s);
            } else {
                out.add(s);
            }
        }
        return out;
    }

    List<QualityCheck> qualityChecks(String raw, CandidateFacts facts) {
        int min = rules.impactBulletsMin();
        List<String> bullets = experienceBullets(raw);
        long withNumber = bullets.stream().filter(b -> IMPACT.matcher(b).find()).count();
        int share = round(100.0 * withNumber / Math.max(bullets.size(), 1));
        boolean led = leadYears(raw, facts) > 0;
        boolean sized = TEAM_SIZE.matcher(raw).find();
        return List.of(
                new QualityCheck("Q1", "Bullets with measurable results", share >= min, share,
                        share + "% of " + bullets.size() + " experience bullets have a number (target " + min + "%)"),
                new QualityCheck("Q2", "Leadership scope stated", sized || !led, null,
                        sized ? "team size stated" : led ? "state how many people you led" : "no lead roles"));
    }

    /** Conditions in the posting a CV can't show (start date, time zone, visa, office). The user confirms them. */
    public List<String> logistics(String jobText) {
        String t = lower(jobText);
        return LOGISTICS.stream().filter(e -> e.getValue().matcher(t).find()).map(Map.Entry::getKey).toList();
    }

    // ---------- match: the CV against one listing ----------

    record MatchOutcome(Map<String, RuleResult> rules, Keywords keywords) {
    }

    MatchOutcome matchListing(String raw, CandidateFacts facts, JobRequirements job) {
        String t = norm(raw);
        String section = section(raw, List.of("experience"), List.of("skills", "education"));
        String exp = section.isEmpty() ? t : section;
        List<String> emps = facts.employers().stream().map(PyText::lower).toList();
        int recentJobs = rules.recentJobs();
        int s = emps.isEmpty() ? -1 : exp.indexOf(emps.get(0));
        int e = emps.size() > recentJobs ? exp.indexOf(emps.get(recentJobs)) : -1;
        String recent = s >= 0 ? exp.substring(s, e > s ? e : exp.length()) : "";
        List<String> lines = PyText.splitLines(raw);
        String head = norm(String.join("\n", lines.subList(0, Math.min(6, lines.size()))));
        Map<String, RuleResult> res = new LinkedHashMap<>();

        List<String> koMiss = new ArrayList<>();
        for (String k : job.knockout()) {
            boolean english = lower(k).equals("english");
            boolean ok = english ? englishLevel(t) : terms.has(k, t);
            if (!ok) {
                koMiss.add(k + (english && t.contains("english") ? " (level not stated)" : ""));
            }
        }
        res.put("M1", new RuleResult("Knockout skills", koMiss.isEmpty(), null,
                koMiss.isEmpty() ? "all present" : "missing: " + String.join(", ", koMiss)));

        Integer yrs = statedYears(t);
        res.put("M2", new RuleResult("Years of experience", yrs != null && yrs >= job.minYears(), null,
                "CV " + (yrs == null ? "None" : yrs) + " vs listing " + orNotStated(job.minYears())));

        List<String> req = job.required();
        List<String> miss = req.stream().filter(k -> !terms.has(k, t)).toList();
        int cov = round(100.0 * (req.size() - miss.size()) / Math.max(req.size(), 1));
        res.put("M3", new RuleResult("Required keyword coverage", cov >= rules.requiredCoverageMin(), cov,
                cov + "% (" + (req.size() - miss.size()) + "/" + req.size() + ")"));

        List<String> found = req.stream().filter(k -> !miss.contains(k)).toList();
        List<String> noEv = found.stream().filter(k -> !terms.has(k, exp)).toList();
        int ev = round(100.0 * (found.size() - noEv.size()) / Math.max(found.size(), 1));
        res.put("M4", new RuleResult("Skills backed by experience bullets", ev >= rules.evidenceMin(), ev, ev + "%"));

        List<String> core = job.knockout().stream().filter(k -> !lower(k).equals("english")).toList();
        long inRecent = core.stream().filter(k -> terms.has(k, recent)).count();
        int rec = round(100.0 * inRecent / Math.max(core.size(), 1));
        res.put("M5", new RuleResult("Core skills in last " + recentJobs + " jobs", rec >= rules.recencyMin(), rec, rec + "%"));

        List<String> words = new ArrayList<>(new LinkedHashSet<>(titleWords(job.title())));
        Set<String> hw = new HashSet<>(titleWords(head));
        List<String> tw = words.stream().filter(w -> hw.contains(w) || hw.stream().anyMatch(h -> h.startsWith(w))).toList();
        List<String> lacking = words.stream().filter(w -> !tw.contains(w)).toList();
        int ta = round(100.0 * tw.size() / Math.max(words.size(), 1));
        res.put("M6", new RuleResult("Headline matches job title", ta >= rules.titleAlignmentMin(), ta,
                ta + "%" + (ta < 100 ? ", headline lacks: " + String.join(", ", lacking) : "")));

        List<String> niceHit = job.nice().stream().filter(k -> terms.has(k, t)).toList();
        int nc = job.nice().isEmpty() ? 100 : round(100.0 * niceHit.size() / Math.max(job.nice().size(), 1));
        res.put("M7", new RuleResult("Nice-to-have coverage", nc >= rules.niceCoverageBonus(), nc, nc + "%"));

        int need = job.minLeadYears();
        int have = leadYears(raw, facts);
        res.put("M8", new RuleResult("Leadership years", have >= need, have, "CV " + have + " vs listing " + orNotStated(need)));

        Keywords keywords = new Keywords(miss, job.nice().stream().filter(k -> !niceHit.contains(k)).toList(), noEv, lacking);
        return new MatchOutcome(res, keywords);
    }

    // ---------- verdict and score ----------

    String verdict(boolean gatesOk, Map<String, RuleResult> r) {
        if (!gatesOk) {
            return "REJECT (fix CV)";
        }
        if (!(r.get("M1").ok() && r.get("M2").ok())) {
            return "REJECT";
        }
        if (!List.of("M3", "M4", "M5", "M8").stream().allMatch(k -> r.get(k).ok())) {
            return "WEAK";
        }
        if (r.get("M3").value() >= rules.requiredCoverageStrong() && r.get("M4").value() >= rules.evidenceStrong()
                && r.get("M6").ok() && r.get("M7").ok()) {
            return "STRONG";
        }
        return "ACCEPTABLE";
    }

    /** 0 to 100 from the rule results (weights in rules.json). */
    int score(List<GateCheck> gates, Map<String, RuleResult> r, List<QualityCheck> quality) {
        double s = (rules.weight("coverage") * r.get("M3").value() + rules.weight("evidence") * r.get("M4").value()
                + rules.weight("recency") * r.get("M5").value() + rules.weight("title") * r.get("M6").value()
                + rules.weight("nice") * r.get("M7").value()) / 100.0;
        s += r.get("M2").ok() ? rules.weight("years") : 0;
        RuleResult m1 = r.get("M1");
        s -= rules.weight("knockout_penalty") * (m1.ok() ? 0 : m1.detail().split(",", -1).length);
        s -= rules.weight("gate_penalty") * gates.stream().filter(g -> !g.ok()).count();
        s -= r.get("M8").ok() ? 0 : rules.weight("lead_penalty");
        s -= rules.weight("quality_penalty") * quality.stream().filter(q -> !q.ok()).count();
        return Math.max(0, Math.min(100, round(s)));
    }

    public String label(int score) {
        for (Map.Entry<Integer, String> l : rules.ratingLabels()) {
            if (score >= l.getKey()) {
                return l.getValue();
            }
        }
        return rules.ratingLabels().get(rules.ratingLabels().size() - 1).getValue();
    }

    // ---------- text helpers ----------

    private static String section(String raw, List<String> startWords, List<String> endWords) {
        List<String> out = new ArrayList<>();
        boolean inside = false;
        for (String ln : PyText.splitLines(raw)) {
            String s = lower(PyText.strip(ln));
            boolean shortLine = PyText.len(s) < 40;
            if (!inside && shortLine && startWords.stream().anyMatch(w -> s.startsWith(w) || s.endsWith(w))) {
                inside = true;
                continue;
            }
            if (inside && shortLine && endWords.stream().anyMatch(s::contains)) {
                break;
            }
            if (inside) {
                out.add(ln);
            }
        }
        return norm(String.join("\n", out));
    }

    private static Integer statedYears(String t) {
        Matcher m = JobParser.YEARS.matcher(t);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static List<String> titleWords(String s) {
        return PyText.findAll(WORD, lower(s)).stream().filter(w -> !STOP.contains(w))
                .map(w -> CANON.getOrDefault(w, w)).toList();
    }

    /** Years in roles titled Lead/Manager (from the dates on the title line), or an explicit "N+ years of leadership". */
    private int leadYears(String raw, CandidateFacts facts) {
        int total = 0;
        for (String ln : PyText.splitLines(raw)) {
            Matcher m = DATE_RANGE.matcher(ln);
            if (!m.find()) {
                continue;
            }
            String title = ln.substring(0, m.start());
            String lowerTitle = lower(title);
            if (LEAD_TITLE.matcher(title).find() && facts.employers().stream().anyMatch(e -> lowerTitle.contains(lower(e)))) {
                String to = m.group(2);
                int end = Character.isDigit(to.charAt(0)) ? Integer.parseInt(to) : currentYear;
                total += Math.max(end - Integer.parseInt(m.group(1)), 1);
            }
        }
        List<Integer> stated = PyText.findAll(STATED_LEAD, raw).stream().map(Integer::parseInt).toList();
        return stated.isEmpty() ? total : Math.max(total, Collections.max(stated));
    }

    private boolean englishLevel(String t) {
        Matcher m = ENGLISH.matcher(t);
        if (!m.find()) {
            return false;
        }
        String level = m.group(1);
        return rules.englishLevels().stream().anyMatch(level::contains);
    }

    private static String orNotStated(int n) {
        return n == 0 ? "not stated" : String.valueOf(n);
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
