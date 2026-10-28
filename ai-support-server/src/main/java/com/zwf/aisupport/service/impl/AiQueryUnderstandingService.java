package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.common.util.TextUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class AiQueryUnderstandingService {

    private static final Pattern WORD_SPLIT_PATTERN = Pattern.compile("[^a-z0-9+#./_-]+");
    private static final Pattern QUESTION_TASK_SPLIT_PATTERN = Pattern.compile(
            "\\?+|;+|\\band\\s+(?=(what|why|how|when|should|does|do|is|are|can)\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "this", "that", "these", "those",
            "in", "on", "at", "to", "for", "from", "with", "without", "after", "before", "behind",
            "why", "what", "how", "when", "where", "which", "who", "does", "do", "did", "is", "are",
            "was", "were", "be", "been", "being", "still", "first", "most", "should", "check", "please",
            "get", "post", "put", "patch", "delete", "head", "options", "try", "tries",
            "i", "we", "you", "my", "our", "your", "it", "its", "of", "by", "as", "into", "about",
            "he", "she", "him", "her", "his", "hers", "they", "them", "their", "theirs", "there",
            "can", "could", "would", "may", "might", "must", "will", "shall",
            "not", "because", "even", "though", "has", "have", "had", "than", "whether", "determine",
            "difference", "between", "versus", "vs"
    );
    private static final Set<String> BROAD_DOMAIN_TERMS = Set.of(
            "auth", "authentication", "authorization", "jwt", "token", "kubernetes", "postgresql",
            "postgres", "redis", "docker", "production", "troubleshooting", "document", "article",
            "knowledge", "platform", "cloudops", "support", "server", "service", "services", "system",
            "container", "containers", "database", "backend", "frontend", "application"
    );
    private static final Set<String> TECHNICAL_DOMAIN_TERMS = Set.of(
            "docker", "postgresql", "postgres", "redis", "kubernetes", "jwt", "token", "ticket"
    );
    private static final Set<String> GENERIC_QUESTION_TERMS = Set.of(
            "rejected", "reject", "expired", "expire", "failure", "failed", "error", "issue",
            "problem", "handle", "configure", "download", "install", "login", "logout", "users",
            "inside", "instead", "communication", "connect", "connects", "connected", "connecting",
            "use", "using", "used", "names", "name", "running", "fail", "fails", "decide", "choose",
            "classify", "classification", "return", "returns", "returned", "purpose", "updated",
            "update", "receives", "forbidden", "normal", "endpoint", "endpoints", "specific",
            "interesting", "interestingly", "directly", "production", "named", "called", "logged", "system",
            "architecture", "given", "likely", "root", "cause", "access", "analytics"
    );
    private static final Set<String> QUERY_CONTEXT_TERMS = Set.of(
            "according", "official", "documentation", "documented", "current", "currently", "latest",
            "stable", "release", "supported", "support", "engineer", "main", "common", "standard",
            "steps", "step", "diagnose", "diagnosed", "explain", "analysis", "analyze", "compare",
            "comparison", "work", "works", "working", "together", "environment", "unavailable",
            "available", "high", "severity", "production", "specific", "exact", "information"
    );

    public AiQueryPlan analyze(String question) {
        String normalizedQuestion = TextUtil.safeLower(question);
        List<String> tasks = splitTasks(question);
        AiAnswerIntent intent = classifyIntent(question);
        LinkedHashSet<String> requiredConcepts = new LinkedHashSet<>();
        for (String task : tasks) {
            extractSearchTerms(task).stream()
                    .filter(term -> !GENERIC_QUESTION_TERMS.contains(term))
                    .filter(term -> !STOP_WORDS.contains(term))
                    .filter(term -> !QUERY_CONTEXT_TERMS.contains(term))
                    .filter(term -> isRequiredConceptTerm(question, term))
                    .forEach(requiredConcepts::add);
        }

        List<String> concepts = requiredConcepts.stream().limit(14).toList();
        List<String> anchors = concepts.stream().filter(term -> isAnchorConcept(question, term)).toList();
        if (anchors.isEmpty() && !concepts.isEmpty()) {
            anchors = concepts.stream().limit(Math.min(2, concepts.size())).toList();
        }

        LinkedHashSet<String> retrievalTerms = new LinkedHashSet<>(concepts);
        extractSearchTerms(question).stream()
                .filter(term -> !GENERIC_QUESTION_TERMS.contains(term))
                .filter(term -> !QUERY_CONTEXT_TERMS.contains(term))
                .filter(term -> !term.matches("\\d{1,2}(:\\d{2})?(am|pm)?"))
                .filter(term -> !term.startsWith("/api/"))
                .forEach(retrievalTerms::add);

        return new AiQueryPlan(
                question,
                tasks,
                concepts,
                anchors,
                expectedOutputsFor(intent, normalizedQuestion),
                retrievalTerms.stream().limit(18).toList(),
                KnowledgeCategoryResolver.inferCandidates(question),
                requiresExternalEvidence(normalizedQuestion),
                intent
        );
    }

    public List<String> extractSearchTerms(String question) {
        String normalized = TextUtil.safeLower(question);
        return WORD_SPLIT_PATTERN.splitAsStream(normalized)
                .map(this::normalizeSearchTerm)
                .filter(this::isSearchableTerm)
                .filter(term -> !STOP_WORDS.contains(term))
                .distinct()
                .toList();
    }

    public List<String> extractSpecificTerms(List<String> terms) {
        Set<String> broadTerms = Set.of(
                "kubernetes", "postgresql", "production", "troubleshooting", "document",
                "article", "knowledge", "platform", "cloudops", "support"
        );
        return terms.stream()
                .filter(term -> !term.matches(".*\\d.*") && term.length() >= 10)
                .filter(term -> !term.startsWith("/api/"))
                .filter(term -> !broadTerms.contains(term))
                .filter(term -> !GENERIC_QUESTION_TERMS.contains(term))
                .toList();
    }

    public List<String> extractVersionTerms(List<String> terms) {
        return terms.stream()
                .filter(term -> !term.matches("\\d{1,2}(:\\d{2})?(am|pm)?"))
                .filter(term -> !term.matches("\\d{1,2}:\\d{2}"))
                .filter(term -> !Set.of("401", "403", "404", "500").contains(term))
                .filter(term -> term.matches(".*\\d.*"))
                .toList();
    }

    public boolean isCreativeOrHypothetical(String question) {
        String normalized = TextUtil.safeLower(question);
        return TextUtil.containsAny(normalized,
                "invent ", "create original", "original names", "fictional", "imagine ",
                "hypothetical", "brainstorm", "creative concept", "made-up", "made up");
    }

    private String normalizeSearchTerm(String term) {
        String normalized = term == null ? "" : term.trim()
                .replaceAll("^[,;:!?()\\[\\]{}\\\"']+|[,;:!?()\\[\\]{}\\\"']+$", "");
        if (normalized.length() > 1 && normalized.endsWith(".") && !normalized.startsWith(".")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isSearchableTerm(String term) {
        return term != null && !term.isBlank()
                && (term.length() >= 3 || term.matches("[a-z]+\\d+") || term.matches("\\d+[a-z]+"));
    }

    private List<String> splitTasks(String question) {
        String normalized = question == null ? "" : question.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> tasks = new LinkedHashSet<>();
        for (String rawPart : QUESTION_TASK_SPLIT_PATTERN.split(normalized)) {
            String part = rawPart.trim();
            if (part.length() >= 8) {
                tasks.add(part);
            }
        }
        return tasks.isEmpty() ? List.of(normalized) : tasks.stream().limit(4).toList();
    }

    private boolean isAnchorConcept(String question, String term) {
        return TECHNICAL_DOMAIN_TERMS.contains(term)
                || term.matches(".*[0-9+#./_-].*")
                || isExplicitIdentifier(question, term)
                || term.length() >= 5;
    }

    private boolean requiresExternalEvidence(String normalizedQuestion) {
        return TextUtil.containsAny(normalizedQuestion,
                "according to the official", "official documentation", "official docs",
                "current official", "currently documented", "latest stable", "latest release",
                "as of today", "as of now", "current version", "supported versions");
    }

    private boolean isRequiredConceptTerm(String question, String term) {
        if (term == null || term.isBlank() || isIncidentalQuestionValue(question, term)) {
            return false;
        }
        if (term.matches("\\d{1,2}(:\\d{2})?") || term.matches("\\d{1,2}:\\d{2}(am|pm)?") || term.startsWith("/api/")) {
            return false;
        }
        if (BROAD_DOMAIN_TERMS.contains(term) && !TECHNICAL_DOMAIN_TERMS.contains(term)) {
            return false;
        }
        return term.matches(".*[0-9+#./-].*") || term.length() >= 4 || isExplicitIdentifier(question, term);
    }

    private boolean isIncidentalQuestionValue(String question, String term) {
        String normalizedTerm = TextUtil.safeLower(term);
        if (("am".equals(normalizedTerm) || "pm".equals(normalizedTerm))
                && question != null
                && Pattern.compile("\\b\\d{1,2}(?::\\d{2})?\\s*(am|pm)\\b", Pattern.CASE_INSENSITIVE).matcher(question).find()) {
            return true;
        }
        return question != null && normalizedTerm.length() >= 3
                && Pattern.compile("\\b(named|called)\\s+" + Pattern.quote(normalizedTerm) + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(question)
                .find();
    }

    private boolean isExplicitIdentifier(String question, String term) {
        if (question == null || term == null || term.length() > 5) {
            return false;
        }
        return Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(term) + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE)
                .matcher(question)
                .results()
                .anyMatch(match -> match.group().equals(match.group().toUpperCase(Locale.ROOT)));
    }

    private List<String> expectedOutputsFor(AiAnswerIntent intent, String normalizedQuestion) {
        LinkedHashSet<String> outputs = new LinkedHashSet<>();
        outputs.add("direct answer");
        if (intent == AiAnswerIntent.DECISION) {
            outputs.add("decision criteria");
            outputs.add("how to apply");
        } else if (intent == AiAnswerIntent.TROUBLESHOOTING) {
            outputs.add("likely causes");
            outputs.add("checks");
            outputs.add("fix");
        } else if (intent == AiAnswerIntent.PROCEDURE) {
            outputs.add("steps");
            outputs.add("verification");
        } else {
            outputs.add("explanation");
            if (TextUtil.containsAny(normalizedQuestion, "when", "should", "use")) {
                outputs.add("when to use");
            }
            outputs.add("example");
        }
        return outputs.stream().toList();
    }

    private AiAnswerIntent classifyIntent(String question) {
        String normalized = TextUtil.safeLower(question);
        boolean troubleshooting = TextUtil.containsAny(normalized,
                "fail", "fails", "failed", "failure", "error", "cannot", "can't", "unable",
                "timeout", "rejected", "reject", "down", "not working", "not connect", "connection refused",
                "keeps", "stuck", "broken", "slow", "latency", "forbidden", "unauthorized")
                || Pattern.compile("\\b[45]\\d{2}\\b").matcher(normalized).find();
        if (troubleshooting) {
            return AiAnswerIntent.TROUBLESHOOTING;
        }
        if (Pattern.compile("\\b(determine|classify|classification|decide|choose|priority|severity|routing|sla)\\b")
                .matcher(normalized)
                .find()) {
            return AiAnswerIntent.DECISION;
        }
        boolean concept = normalized.startsWith("what is")
                || normalized.startsWith("what are")
                || normalized.startsWith("why does")
                || normalized.startsWith("why do")
                || normalized.startsWith("why should")
                || normalized.startsWith("how does")
                || normalized.startsWith("how do")
                || normalized.contains("purpose")
                || normalized.contains("when should")
                || normalized.contains("explain")
                || normalized.contains("improve");
        if (concept) {
            return AiAnswerIntent.CONCEPT;
        }
        boolean procedure = normalized.startsWith("how to")
                || normalized.startsWith("how can")
                || normalized.contains("steps")
                || normalized.contains("configure")
                || normalized.contains("install");
        return procedure ? AiAnswerIntent.PROCEDURE : AiAnswerIntent.CONCEPT;
    }
}
