package com.hiessy.jobagent.posting;

import com.hiessy.jobagent.config.JobAgentProperties;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** GETs the page, following up to three redirects, each one checked by {@link UrlGuard}. Tests replace it with a {@code @Primary} source. */
@Component
public class HttpPostingSource implements PostingSource {

    private static final Pattern CHARSET = Pattern.compile("charset=([\\w.-]+)", Pattern.CASE_INSENSITIVE);
    private static final int MAX_REDIRECTS = 3;

    private final HttpClient http;
    private final UrlGuard guard;
    private final JobAgentProperties.Fetch cfg;

    public HttpPostingSource(HttpClient http, UrlGuard guard, JobAgentProperties props) {
        this.http = http;
        this.guard = guard;
        this.cfg = props.fetch();
    }

    @Override
    public Page load(URI url) throws IOException {
        URI current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpRequest req = HttpRequest.newBuilder(current).timeout(cfg.timeout()).GET()
                    .header("User-Agent", cfg.userAgent())
                    .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                    .header("Accept-Language", "en,es;q=0.8")
                    .build();
            HttpResponse<InputStream> res;
            try {
                res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + current, e);
            }
            int status = res.statusCode();
            if (status >= 300 && status < 400) {
                res.body().close();
                String location = res.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("Redirect without a Location header"));
                current = guard.check(current.resolve(location).toString());
                continue;
            }
            try (InputStream in = res.body()) {
                if (status >= 400) {
                    throw new IOException("The site answered HTTP " + status);
                }
                byte[] bytes = in.readNBytes(cfg.maxBytes());
                String type = res.headers().firstValue("Content-Type").orElse("text/html");
                return new Page(current, new String(bytes, charset(type)), type);
            }
        }
        throw new IOException("Too many redirects");
    }

    private static Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalArgumentException ignored) {
                // fall through to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }
}
