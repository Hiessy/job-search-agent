package com.hiessy.jobagent.cv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiessy.jobagent.config.JobAgentProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * The candidate's CV (plus any tailored variants) and the facts the gate rules check. Loaded once at
 * startup from the paths in {@code jobagent.cv}; the defaults are a fictional sample candidate.
 */
@Component
public class CvProfile {

    public static final String PRIMARY = "default";
    private static final Logger log = LoggerFactory.getLogger(CvProfile.class);

    /** One CV text. {@code name} is "default" for the main CV, else the variant name. */
    public record CvDocument(String name, String text) {
    }

    private final CandidateFacts facts;
    private final Map<String, CvDocument> documents;

    @Autowired
    public CvProfile(JobAgentProperties props, ResourceLoader resources, ObjectMapper mapper) {
        JobAgentProperties.Cv cfg = props.cv();
        this.facts = readFacts(resources.getResource(cfg.factsPath()), mapper);
        Map<String, CvDocument> docs = new LinkedHashMap<>();
        docs.put(PRIMARY, new CvDocument(PRIMARY, readCv(resources.getResource(cfg.path()), cfg.pdftotext())));
        cfg.variants().forEach((name, path) ->
                docs.put(name, new CvDocument(name, readCv(resources.getResource(path), cfg.pdftotext()))));
        this.documents = Map.copyOf(docs);
        log.info("CV loaded from {} ({} variant(s)), candidate facts from {}", cfg.path(), cfg.variants().size(),
                cfg.factsPath());
    }

    /** For tests. */
    public CvProfile(CandidateFacts facts, List<CvDocument> documents) {
        this.facts = facts;
        Map<String, CvDocument> docs = new LinkedHashMap<>();
        documents.forEach(d -> docs.put(d.name(), d));
        this.documents = Map.copyOf(docs);
    }

    public CandidateFacts facts() {
        return facts;
    }

    public CvDocument primary() {
        return documents.get(PRIMARY);
    }

    /** The main CV first, then the variants. */
    public List<CvDocument> all() {
        return documents.values().stream()
                .sorted((a, b) -> a.name().equals(PRIMARY) ? -1 : b.name().equals(PRIMARY) ? 1 : a.name().compareTo(b.name()))
                .toList();
    }

    private static CandidateFacts readFacts(Resource resource, ObjectMapper mapper) {
        try (InputStream in = resource.getInputStream()) {
            CandidateFacts facts = mapper.readValue(in, CandidateFacts.class);
            if (facts.email() == null || facts.phoneDigits() == null || facts.employers().isEmpty()) {
                throw new IllegalStateException("Candidate facts need email, phone_digits and employers: " + resource);
            }
            return facts;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read candidate facts " + resource, e);
        }
    }

    private static String readCv(Resource resource, String pdftotext) {
        try {
            String name = resource.getFilename() == null ? "" : resource.getFilename().toLowerCase();
            if (name.endsWith(".pdf")) {
                return pdfToText(resource.getFile().toPath(), pdftotext);
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read CV " + resource, e);
        }
    }

    /** Same call as core.py's pdf_to_text, so both engines see the same text. */
    private static String pdfToText(Path pdf, String pdftotext) throws IOException {
        if (!Files.isRegularFile(pdf)) {
            throw new IOException("No such file: " + pdf);
        }
        Process p = new ProcessBuilder(pdftotext, "-enc", "UTF-8", "-layout", pdf.toString(), "-")
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        byte[] out = p.getInputStream().readAllBytes();
        try {
            if (!p.waitFor(30, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IOException("pdftotext failed on " + pdf);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading " + pdf, e);
        }
        return new String(out, StandardCharsets.UTF_8);
    }
}
