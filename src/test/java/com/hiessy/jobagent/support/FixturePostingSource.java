package com.hiessy.jobagent.support;

import com.hiessy.jobagent.posting.PostingSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Serves the saved postings in src/test/resources/postings by the URL on their "Source:" line, so
 * tests and the eval set run without the network and always see the same text.
 */
public class FixturePostingSource implements PostingSource {

    public static final Path DIR = Path.of("src", "test", "resources", "postings");
    private static final Pattern SOURCE = Pattern.compile("^Source:\\s*(\\S+)", Pattern.MULTILINE);

    private final Map<String, String> byUrl = new HashMap<>();

    public FixturePostingSource() {
        try (Stream<Path> files = Files.list(DIR)) {
            files.filter(f -> f.toString().endsWith(".txt")).forEach(f -> {
                String text = read(f);
                Matcher m = SOURCE.matcher(text);
                if (m.find()) {
                    byUrl.put(normalise(m.group(1)), text);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String read(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The URL on a posting file's Source line. */
    public static String urlOf(String fileName) {
        Matcher m = SOURCE.matcher(read(DIR.resolve(fileName)));
        if (!m.find()) {
            throw new IllegalArgumentException(fileName + " has no Source line");
        }
        return m.group(1);
    }

    @Override
    public Page load(URI url) throws IOException {
        String text = byUrl.get(normalise(url.toString()));
        if (text == null) {
            throw new IOException("The site answered HTTP 404");
        }
        return new Page(url, text, "text/plain; charset=utf-8");
    }

    private static String normalise(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
