package com.hiessy.jobagent.tools;

import com.hiessy.jobagent.cv.CvProfile;
import com.hiessy.jobagent.cv.CvProfile.CvDocument;
import com.hiessy.jobagent.cv.CvRuleEngine;
import com.hiessy.jobagent.cv.Evaluation;
import com.hiessy.jobagent.guardrails.ToolOutput;
import com.hiessy.jobagent.posting.PostingStore.Posting;
import com.hiessy.jobagent.tools.ToolResults.VariantComparison;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Lays the tailored CV versions side by side for one posting. Rules handle what rules are good at
 * (knockouts and gaps); the variants share the same facts, so their keyword scores barely differ.
 * The difference is emphasis, which the model judges from each headline and summary.
 */
@Component
public class CvVariants {

    static final String HOW_TO_CHOOSE = "Rule scores barely differ between versions because they share the same facts. "
            + "Drop versions with missing_knockouts, then recommend the one whose headline and summary put this posting's "
            + "main requirements first. Name it exactly as in its name field and say in one sentence why.";

    private final CvRuleEngine engine;
    private final CvProfile profile;

    public CvVariants(CvRuleEngine engine, CvProfile profile) {
        this.engine = engine;
        this.profile = profile;
    }

    public VariantComparison compare(Posting posting) {
        List<VariantComparison.Variant> out = new ArrayList<>();
        for (CvDocument cv : profile.all()) {
            Evaluation e = engine.evaluate(cv.text(), profile.facts(), posting.text());
            Evaluation.RuleResult m1 = e.match().get("M1");
            List<String> missingKo = m1.ok() ? List.of()
                    : Arrays.stream(m1.detail().replaceFirst("^missing: ", "").split(", ")).toList();
            out.add(new VariantComparison.Variant(cv.name(), e.verdict(), e.score(), missingKo,
                    e.keywords().missingRequired(), headline(cv.text()), summary(cv.text())));
        }
        String how = out.size() == 1 ? "Only one CV is configured (set jobagent.cv.variants to compare versions)."
                : HOW_TO_CHOOSE;
        return new VariantComparison(ToolOutput.OK, posting.id(), posting.title(), out, how);
    }

    /** The line under the name. */
    static String headline(String cv) {
        return cv.lines().map(String::strip).filter(s -> !s.isEmpty()).skip(1).findFirst().map(s -> cut(s, 200)).orElse("");
    }

    /** The paragraph under a "Summary" or "Profile" heading. */
    static String summary(String cv) {
        List<String> lines = cv.lines().map(String::strip).toList();
        StringBuilder sb = new StringBuilder();
        boolean inside = false;
        for (String ln : lines) {
            String l = ln.toLowerCase();
            if (!inside && ln.length() < 40 && (l.equals("summary") || l.equals("profile") || l.equals("professional summary"))) {
                inside = true;
                continue;
            }
            if (inside && !ln.isEmpty() && ln.length() < 40 && ln.equals(ln.toUpperCase())) {
                break;
            }
            if (inside && !ln.isEmpty()) {
                sb.append(sb.isEmpty() ? "" : " ").append(ln);
            }
        }
        return cut(sb.toString(), 600);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
