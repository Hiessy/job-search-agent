package com.hiessy.jobagent.guardrails;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags text that tries to give orders to an AI tool: "ignore previous instructions", chat-template
 * role tokens, requests to call this agent's tools, to reveal the prompt or to rate the candidate a
 * certain way, plus clusters of invisible characters. Posting text is third-party input, so a hit
 * means the agent refuses the posting instead of reading it.
 *
 * <p>Patterns are narrow on purpose: real postings say "AI agents", "tool calling" and "act as a tech
 * lead", and must not be flagged (see PromptInjectionDetectorTest and the eval set).
 */
@Component
public class PromptInjectionDetector {

    /** {@code evidence} quotes up to three short matches. */
    public record Finding(boolean flagged, List<String> evidence) {

        public String summary() {
            return String.join("; ", evidence);
        }
    }

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE;
    private static final String AI = "(?:ai|a\\.i\\.|llm|gpt|chatgpt|claude|gemini|assistant|chatbot|bot|agent|model|language model|screener)s?";

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("\\b(?:ignore|disregard|forget|override|bypass)\\s+(?:all\\s+|any\\s+|the\\s+|your\\s+|every\\s+)*"
                    + "(?:previous|prior|above|earlier|preceding|system|original|other)\\s+"
                    + "(?:instructions?|prompts?|messages?|rules|directions|guidelines)", FLAGS),
            Pattern.compile("\\b(?:ignora|olvida|omite)\\s+(?:todas\\s+)?(?:las\\s+)?(?:instrucciones|indicaciones)"
                    + "(?:\\s+(?:anteriores|previas))?", FLAGS),
            Pattern.compile("\\b(?:note|message|instructions?|attention)\\s+(?:to|for)\\s+(?:the\\s+|any\\s+|all\\s+)?" + AI + "\\b", FLAGS),
            Pattern.compile("\\b" + AI + "\\s+(?:reading|processing|parsing|screening|evaluating|reviewing|summari[sz]ing)\\s+this\\b", FLAGS),
            Pattern.compile("\\byou\\s+are\\s+now\\s+(?:a|an|the|in)\\b", FLAGS),
            Pattern.compile("\\bfrom\\s+now\\s+on,?\\s+you\\s+(?:are|will|must|should)\\b", FLAGS),
            Pattern.compile("<\\|(?:im_start|im_end|system|user|assistant|endoftext)\\|>|\\[/?INST]|<<\\/?SYS>>|</?system>", FLAGS),
            Pattern.compile("^\\s*#{2,}\\s*(?:system|instructions?)\\b", FLAGS),
            Pattern.compile("\\b(?:fetch_job_posting|score_cv|save_application|list_applications|draft_cover_note|compare_cv_variants)\\b", FLAGS),
            Pattern.compile("\\b(?:rate|score|mark|rank|classify|label)\\s+(?:this|the|every|each|all|any)\\s+"
                    + "(?:candidate|cv|resume|résumé|applicant|application)s?\\s+(?:as|with|at)\\b", FLAGS),
            Pattern.compile("\\b(?:reveal|print|show|output|repeat|leak)\\s+(?:me\\s+)?(?:your|the)\\s+"
                    + "(?:system\\s+prompt|prompt|instructions|api\\s+keys?|secrets?|credentials)", FLAGS),
            Pattern.compile("\\b(?:send|post|upload|forward|email)\\b[^.\\n]{0,60}\\b(?:the\\s+)?(?:user|candidate)'?s\\s+"
                    + "(?:cv|resume|data|details|information|prompt)\\b", FLAGS));

    /** Zero-width and direction-override characters: three or more hint at hidden text. */
    private static final Pattern HIDDEN = Pattern.compile("[​-‏‪-‮⁠-⁤﻿]");

    public Finding scan(String text) {
        if (text == null || text.isEmpty()) {
            return new Finding(false, List.of());
        }
        List<String> evidence = new ArrayList<>();
        for (Pattern p : PATTERNS) {
            Matcher m = p.matcher(text);
            if (m.find() && evidence.size() < 3) {
                evidence.add("\"" + quote(m.group()) + "\"");
            }
        }
        long hidden = HIDDEN.matcher(text).results().count();
        if (hidden >= 3 && evidence.size() < 3) {
            evidence.add(hidden + " invisible characters");
        }
        return new Finding(!evidence.isEmpty(), List.copyOf(evidence));
    }

    private static String quote(String s) {
        String one = s.replaceAll("\\s+", " ").strip();
        return one.length() > 60 ? one.substring(0, 57) + "..." : one;
    }
}
