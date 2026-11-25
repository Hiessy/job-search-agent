package com.hiessy.jobagent.posting;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Postings read in this process, by short id ("P1", "P2"...). Tools pass the id instead of the
 * whole text, which keeps the model's context small and stops it from rewriting the posting on the
 * way between tools. Keeps the most recent 200.
 */
@Component
public class PostingStore {

    public static final Pattern ID = Pattern.compile("^P\\d{1,6}$");
    private static final int MAX = 200;

    /** A posting as the tools see it. {@code url} is null for pasted text; {@code company} may be unknown. */
    public record Posting(String id, String url, String title, String company, String text) {
    }

    private final AtomicInteger seq = new AtomicInteger();
    private final Map<String, Posting> byId = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Posting> eldest) {
            return size() > MAX;
        }
    };

    public synchronized Posting add(String url, String title, String company, String text) {
        Posting p = new Posting("P" + seq.incrementAndGet(), url, title, company, text);
        byId.put(p.id(), p);
        return p;
    }

    public synchronized Optional<Posting> get(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id.strip().toUpperCase()));
    }

    public static boolean isId(String s) {
        return s != null && ID.matcher(s.strip().toUpperCase()).matches();
    }
}
