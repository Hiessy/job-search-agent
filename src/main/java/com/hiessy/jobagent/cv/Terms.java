package com.hiessy.jobagent.cv;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Keyword lookup with synonyms: core.py's {@code alts()} and {@code has()}. */
final class Terms {

    private final RuleSet rules;
    private final Map<String, Pattern> patterns = new ConcurrentHashMap<>();

    Terms(RuleSet rules) {
        this.rules = rules;
    }

    /** Spellings that count for a term: synonyms first, then the vocabulary, then the term itself. */
    List<String> alts(String term) {
        String t = PyText.lower(term);
        List<String> syn = rules.synonyms().get(t);
        if (syn != null && !syn.isEmpty()) {
            return syn;
        }
        List<String> voc = rules.vocab().get(t);
        if (voc != null && !voc.isEmpty()) {
            return voc;
        }
        return List.of(t);
    }

    /** True when any spelling of the term appears in the (already normalised) text as a whole word. */
    boolean has(String term, String text) {
        for (String alt : alts(term)) {
            if (pattern(alt).matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private Pattern pattern(String alt) {
        return patterns.computeIfAbsent(alt,
                a -> Pattern.compile("(?<![a-z0-9])" + Pattern.quote(a) + "(?![a-z0-9])"));
    }
}
