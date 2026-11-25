package com.hiessy.jobagent.cv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python string semantics that the rule engine depends on. The rules were written in Python
 * (worker/cv-eval/core.py); these helpers keep the Java port's results identical, including
 * {@code str.splitlines()}, {@code str.strip()}, {@code \s} and banker's rounding.
 */
final class PyText {

    /** Flags that make a Java pattern read {@code \d \s \w \b} the way Python 3 does for str patterns. */
    static final int PY = Pattern.UNICODE_CHARACTER_CLASS | Pattern.UNIX_LINES;
    static final int PY_I = PY | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private PyText() {
    }

    /** Python {@code str.isspace()} for one char. */
    static boolean isSpace(char c) {
        return c == ' ' || (c >= 0x09 && c <= 0x0d) || (c >= 0x1c && c <= 0x1f) || c == 0x85 || c == 0xa0
                || c == 0x1680 || (c >= 0x2000 && c <= 0x200a) || c == 0x2028 || c == 0x2029 || c == 0x202f
                || c == 0x205f || c == 0x3000;
    }

    private static boolean isLineBreak(char c) {
        return c == '\n' || c == '\r' || c == 0x0b || c == 0x0c || c == 0x1c || c == 0x1d || c == 0x1e
                || c == 0x85 || c == 0x2028 || c == 0x2029;
    }

    /** Python {@code str.splitlines()}. */
    static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (isLineBreak(c)) {
                out.add(s.substring(start, i));
                if (c == '\r' && i + 1 < n && s.charAt(i + 1) == '\n') {
                    i++;
                }
                start = ++i;
            } else {
                i++;
            }
        }
        if (start < n) {
            out.add(s.substring(start));
        }
        return out;
    }

    /** Python {@code str.strip()}. */
    static String strip(String s) {
        int b = 0;
        int e = s.length();
        while (b < e && isSpace(s.charAt(b))) {
            b++;
        }
        while (e > b && isSpace(s.charAt(e - 1))) {
            e--;
        }
        return s.substring(b, e);
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    /** {@code re.sub(r"\s+", " ", s.lower())}. */
    static String norm(String s) {
        String lower = lower(s);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean inSpace = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (isSpace(c)) {
                if (!inSpace) {
                    sb.append(' ');
                }
                inSpace = true;
            } else {
                sb.append(c);
                inSpace = false;
            }
        }
        return sb.toString();
    }

    /** Python 3 {@code round()}: halves go to the even neighbour. */
    static int round(double v) {
        return (int) Math.rint(v);
    }

    /** Python {@code str.isupper()}: at least one cased character and no lowercase ones. */
    static boolean isUpper(String s) {
        boolean cased = false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLowerCase(cp) || Character.isTitleCase(cp)) {
                return false;
            }
            cased |= Character.isUpperCase(cp);
            i += Character.charCount(cp);
        }
        return cased;
    }

    /** Python {@code len()} counts code points, not UTF-16 units. */
    static int len(String s) {
        return s.codePointCount(0, s.length());
    }

    /** Python {@code s[:n]}. */
    static String head(String s, int n) {
        return len(s) <= n ? s : s.substring(0, s.offsetByCodePoints(0, n));
    }

    /** Python {@code re.findall} for a pattern with one group. */
    static List<String> findAll(Pattern p, String s) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(s);
        while (m.find()) {
            out.add(m.groupCount() > 0 ? m.group(1) : m.group());
        }
        return out;
    }

    /** Python {@code repr(list_of_str)}, used in one rule label. */
    static String repr(List<String> items) {
        List<String> quoted = new ArrayList<>();
        for (String s : items) {
            boolean dq = s.contains("'") && !s.contains("\"");
            String q = dq ? "\"" : "'";
            String body = s.replace("\\", "\\\\");
            if (!dq) {
                body = body.replace("'", "\\'");
            }
            quoted.add(q + body + q);
        }
        return "[" + String.join(", ", quoted) + "]";
    }
}
