package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.TextUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeExtractionStage {

    public List<String> extractOperationalPoints(String content) {
        List<String> points = new ArrayList<>();
        for (String rawSentence : content.split("(?<=[.!?])\\s+|\\R+")) {
            String sentence = normalizeLine(rawSentence);
            if (isOperationalSentence(sentence) && !points.contains(sentence)) {
                points.add(TextUtil.limit(sentence, 220));
            }
            if (points.size() >= 5) {
                return points;
            }
        }

        if (!points.isEmpty()) {
            return points;
        }
        return List.of(TextUtil.limit(normalizeLine(content), 220));
    }

    public int scoreLearningValue(String documentCategory, String filename, String content) {
        String text = TextUtil.normalizeSearchText(documentCategory + " " + filename + " " + content);
        int score = 0;
        score += countMatches(text, "production", "incident", "outage", "down", "failure", "error", "timeout", "latency") * 3;
        score += countMatches(text, "verify", "check", "diagnose", "troubleshoot", "rollback", "restart", "configure") * 2;
        score += countMatches(text, "redis", "postgres", "postgresql", "docker", "kubernetes", "jwt", "token", "cache", "database") * 2;
        score += countMatches(text, "command", "sql", "log", "metric", "dashboard", "alert", "sla", "owner") * 2;
        if (content != null && content.length() >= 180) {
            score += 2;
        }
        if (content != null && content.length() >= 400) {
            score += 1;
        }
        return score;
    }

    public String firstUsefulSentence(String content) {
        for (String rawSentence : content.split("(?<=[.!?])\\s+|\\R+")) {
            String sentence = normalizeLine(rawSentence);
            if (sentence.length() >= 18) {
                return sentence;
            }
        }
        return "Uploaded document knowledge";
    }

    private boolean isOperationalSentence(String sentence) {
        String normalized = TextUtil.normalizeSearchText(sentence);
        return sentence.length() >= 24
                && TextUtil.containsAny(normalized,
                "check", "verify", "confirm", "diagnose", "configure", "restart", "rollback",
                "redis", "postgres", "postgresql", "docker", "kubernetes", "jwt", "token",
                "production", "incident", "error", "timeout", "log", "metric", "alert", "sla");
    }

    private int countMatches(String text, String... terms) {
        int score = 0;
        for (String term : terms) {
            if (text.contains(term)) {
                score++;
            }
        }
        return score;
    }

    private String normalizeLine(String value) {
        return value == null ? "" : value
                .replaceAll("^\\s*[-*]+\\s*", "")
                .replaceAll("^\\s*\\d+[.)]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
