package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class KnowledgePackReorganizer {

    private static final int TARGET_SCORE_THRESHOLD = 7;

    private final KnowledgeArticleMapper knowledgeArticleMapper;

    public KnowledgePackReorganizer(KnowledgeArticleMapper knowledgeArticleMapper) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
    }

    public KnowledgeArticle reorganizeOrCreate(String title,
                                               String category,
                                               String content,
                                               Long currentUserId,
                                               String sourceLabel) {
        validateKnowledge(content);
        Optional<KnowledgeArticle> target = findBestTarget(title, category, content);
        if (target.isPresent()) {
            return reorganizeExisting(target.get(), title, category, content, sourceLabel);
        }

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(cleanTitle(title, category));
        article.setCategory(safeCategory(category));
        article.setContent(buildKnowledgePackContent(null, title, category, content, sourceLabel));
        article.setCreatedBy(currentUserId);
        article.setStatus("PUBLISHED");
        knowledgeArticleMapper.insert(article);
        return article;
    }

    public KnowledgeArticle reorganizeExistingById(Long articleId,
                                                   String title,
                                                   String category,
                                                   String content,
                                                   String sourceLabel) {
        KnowledgeArticle article = knowledgeArticleMapper.findById(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BusinessException("Knowledge Pack is no longer available");
        }
        ensureSameDomain(article, category);
        return reorganizeExisting(article, title, category, content, sourceLabel);
    }

    public KnowledgeArticle rewriteSingleArticle(Long articleId,
                                                 String title,
                                                 String category,
                                                 String content,
                                                 String sourceLabel) {
        KnowledgeArticle article = knowledgeArticleMapper.findById(articleId);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BusinessException("Knowledge article not found");
        }
        article.setTitle(cleanTitle(title, safeCategory(category)));
        article.setCategory(safeCategory(category));
        article.setContent(buildKnowledgePackContent(null, title, category, content, sourceLabel));
        knowledgeArticleMapper.update(article);
        return reload(article);
    }

    private KnowledgeArticle reorganizeExisting(KnowledgeArticle article,
                                                String title,
                                                String category,
                                                String content,
                                                String sourceLabel) {
        article.setTitle(cleanTitle(article.getTitle(), category));
        article.setCategory(article.getCategory() == null || article.getCategory().isBlank()
                ? safeCategory(category)
                : article.getCategory());
        article.setContent(buildKnowledgePackContent(article, title, category, content, sourceLabel));
        knowledgeArticleMapper.update(article);
        return reload(article);
    }

    private Optional<KnowledgeArticle> findBestTarget(String title, String category, String content) {
        String incomingDomain = normalizeDomain(category);
        List<String> terms = extractTerms(title + " " + safeCategory(category) + " " + content);
        return knowledgeArticleMapper.findPublishedArticles().stream()
                .filter(article -> sameDomain(article.getCategory(), incomingDomain))
                .map(article -> new ArticleScore(article, scoreTarget(article, category, terms)))
                .filter(score -> score.score() >= TARGET_SCORE_THRESHOLD)
                .max(Comparator.comparingInt(ArticleScore::score))
                .map(ArticleScore::article);
    }

    private int scoreTarget(KnowledgeArticle article, String category, List<String> terms) {
        String articleText = TextUtil.normalizeSearchText(article.getTitle() + " " + article.getCategory() + " " + article.getContent());
        int score = 0;
        if (category != null && article.getCategory() != null && category.equalsIgnoreCase(article.getCategory())) {
            score += 6;
        }
        for (String term : terms) {
            if (articleText.contains(term)) {
                score += term.length() >= 8 ? 2 : 1;
            }
        }
        return score;
    }

    private String buildKnowledgePackContent(KnowledgeArticle existingArticle,
                                             String incomingTitle,
                                             String category,
                                             String incomingContent,
                                             String sourceLabel) {
        String title = existingArticle == null
                ? cleanTitle(incomingTitle, category)
                : cleanTitle(existingArticle.getTitle(), category);
        String resolvedCategory = existingArticle != null && existingArticle.getCategory() != null && !existingArticle.getCategory().isBlank()
                ? existingArticle.getCategory()
                : safeCategory(category);

        List<String> knowledgePoints = existingArticle == null
                ? collectKnowledgePoints(incomingContent)
                : collectKnowledgePoints(existingArticle.getContent(), incomingContent);
        SectionBuckets buckets = organizeKnowledgePoints(knowledgePoints);

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n")
                .append("Category: ").append(resolvedCategory).append("\n\n")
                .append("## Overview\n")
                .append("This Knowledge Pack covers ").append(resolvedCategory)
                .append(" knowledge for AegisCloud support. It explains the durable concepts, configuration points, workflows, and operational checks that support teams need.\n\n")
                .append("## Core Concepts\n")
                .append(formatSection(buckets.coreConcepts(), "- Add concise durable concepts for this Knowledge Pack.")).append("\n\n");

        appendOptionalSection(builder, "Configuration", buckets.configuration());
        appendOptionalSection(builder, "Workflow", buckets.workflow());

        builder.append("## Troubleshooting\n")
                .append(formatSection(buckets.troubleshooting(), "- Add concrete checks, logs, metrics, commands, and escalation signals.")).append("\n\n")
                .append("## Best Practices\n")
                .append(formatSection(buckets.bestPractices(), "- Keep this Knowledge Pack focused, deduplicated, and easy to use.")).append("\n\n")
                .append("## References\n")
                .append("- Add verified official documentation links or approved internal references when they are safe to expose.\n");

        return removeProcessArtifacts(builder.toString()).trim();
    }

    private void appendOptionalSection(StringBuilder builder, String heading, List<String> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        builder.append("## ").append(heading).append("\n")
                .append(formatSection(points, "")).append("\n\n");
    }

    private SectionBuckets organizeKnowledgePoints(List<String> points) {
        LinkedHashSet<String> core = new LinkedHashSet<>();
        LinkedHashSet<String> configuration = new LinkedHashSet<>();
        LinkedHashSet<String> workflow = new LinkedHashSet<>();
        LinkedHashSet<String> troubleshooting = new LinkedHashSet<>();
        LinkedHashSet<String> bestPractices = new LinkedHashSet<>();

        for (String point : points) {
            String normalized = point.toLowerCase(Locale.ROOT);
            if (TextUtil.containsAny(normalized, "configure", "configuration", "setting", "property", "environment", "yaml", "port", "timeout", "token", "secret")) {
                configuration.add(point);
            } else if (TextUtil.containsAny(normalized, "workflow", "step", "process", "pipeline", "flow", "before", "after")) {
                workflow.add(point);
            } else if (TextUtil.containsAny(normalized, "error", "fail", "failure", "down", "timeout", "check", "verify", "diagnose", "log", "metric", "alert", "issue")) {
                troubleshooting.add(point);
            } else if (TextUtil.containsAny(normalized, "should", "must", "avoid", "recommend", "best practice", "prevent", "keep", "ensure")) {
                bestPractices.add(point);
            } else {
                core.add(point);
            }
        }

        moveOverflow(core, troubleshooting, 10);
        return new SectionBuckets(
                new ArrayList<>(core),
                new ArrayList<>(configuration),
                new ArrayList<>(workflow),
                new ArrayList<>(troubleshooting),
                new ArrayList<>(bestPractices)
        );
    }

    private List<String> collectKnowledgePoints(String... contents) {
        Map<String, String> points = new LinkedHashMap<>();
        for (String content : contents) {
            if (content == null || content.isBlank()) {
                continue;
            }
            String normalized = removeArticleMetadata(content);
            for (String raw : normalized.split("\\R+|(?<=[.!?])\\s+")) {
                String point = normalizePoint(raw);
                if (isDurableKnowledgePoint(point)) {
                    points.putIfAbsent(signature(point), point);
                }
            }
        }
        return new ArrayList<>(points.values());
    }

    private String removeArticleMetadata(String content) {
        return content
                .replaceAll("(?im)^#+\\s*", "")
                .replaceAll("(?im)^Title\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Purpose\\s*:?\\s*.*$", "")
                .replaceAll("(?im)^Category\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Knowledge Candidate\\s*:?\\s*.*$", "")
                .replaceAll("(?im)^Source references\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source document\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source chunk\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Knowledge to reorganize\\s*:?\\s*$", "")
                .replaceAll("(?im)^Key operational knowledge\\s*:?\\s*$", "")
                .replaceAll("(?im)^Review checklist\\s*:?\\s*$", "")
                .replaceAll("(?im)^Knowledge architecture checklist\\s*:?\\s*$", "")
                .replaceAll("(?im)^References\\s*$", "")
                .replaceAll("(?im)^Maintenance rule\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Chunk rule\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source type\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source domain\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source URL\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Original question\\s*:\\s*.*$", "")
                .replaceAll("(?im)^.*(course|bootcamp|training|certification|enroll|signup|sign up|pricing|advertisement|sponsored).*$", "");
    }

    private String normalizePoint(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("^\\s*[-*]\\s*", "")
                .replaceAll("^\\s*\\d+[.)]\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isDurableKnowledgePoint(String point) {
        String normalized = point.toLowerCase(Locale.ROOT);
        return point.length() >= 24
                && !normalized.startsWith("overview")
                && !normalized.startsWith("core concepts")
                && !normalized.startsWith("configuration")
                && !normalized.startsWith("workflow")
                && !normalized.startsWith("troubleshooting")
                && !normalized.startsWith("best practices")
                && !normalized.startsWith("references")
                && !normalized.startsWith("source:")
                && !normalized.startsWith("title:")
                && !normalized.startsWith("purpose:")
                && !normalized.contains("knowledge candidate")
                && !normalized.contains("knowledge architecture checklist")
                && !normalized.contains("review checklist")
                && !normalized.contains("advertisement")
                && !normalized.contains("sponsored")
                && !normalized.contains("course")
                && !normalized.contains("bootcamp")
                && !normalized.contains("certification")
                && !normalized.contains("enroll now")
                && !normalized.contains("sign up")
                && !normalized.contains("pricing")
                && !normalized.contains("reviewed by administrator")
                && !normalized.contains("administrator review")
                && !normalized.contains("approval")
                && !normalized.contains("retrieval chunk")
                && !normalized.contains("source chunk")
                && !normalized.contains("source document")
                && !normalized.contains("chunk rule:")
                && !normalized.contains("maintenance rule:")
                && !normalized.contains("create a new pack only")
                && !normalized.contains("place this knowledge in the closest existing knowledge pack")
                && !normalized.contains("rewrite the affected section")
                && !normalized.contains("remove duplicate or low-density wording")
                && !normalized.contains("reorganize retrieval chunks")
                && !normalized.contains("this draft captures");
    }

    private String formatSection(List<String> points, String fallback) {
        if (points == null || points.isEmpty()) {
            return fallback == null || fallback.isBlank() ? "- Add reviewed knowledge for this section." : fallback;
        }
        StringBuilder builder = new StringBuilder();
        points.stream()
                .limit(14)
                .forEach(point -> builder.append("- ").append(TextUtil.limit(point, 280)).append("\n"));
        return builder.toString().trim();
    }

    private List<String> extractTerms(String text) {
        String normalized = TextUtil.normalizeSearchText(text);
        List<String> terms = new ArrayList<>();
        for (String term : normalized.split("[^a-z0-9+#./-]+")) {
            if (term.length() >= 4 && !terms.contains(term)) {
                terms.add(term);
            }
            if (terms.size() >= 14) {
                break;
            }
        }
        return terms;
    }

    private String signature(String point) {
        String signature = TextUtil.normalizeSearchText(point).replaceAll("[^a-z0-9]+", "");
        return signature.length() <= 120 ? signature : signature.substring(0, 120);
    }

    private String cleanTitle(String title, String category) {
        String cleaned = title == null || title.isBlank() ? safeCategory(category) + " Knowledge Pack" : title.trim();
        cleaned = cleaned
                .replaceFirst("^Document Insight:\\s*", "")
                .replaceFirst("^Knowledge Candidate:\\s*", "")
                .replaceFirst("^Reviewed Model Answer:\\s*", "")
                .replaceFirst("^Reviewed Web Answer:\\s*", "")
                .replaceFirst("^External Evidence:\\s*", "");
        if (!cleaned.toLowerCase(Locale.ROOT).contains("knowledge pack")) {
            cleaned = cleaned + " Knowledge Pack";
        }
        return TextUtil.limit(cleaned, 180);
    }

    private String safeCategory(String category) {
        return category == null || category.isBlank() ? "General" : category.trim();
    }

    private void ensureSameDomain(KnowledgeArticle article, String incomingCategory) {
        if (!sameDomain(article.getCategory(), normalizeDomain(incomingCategory))) {
            throw new BusinessException("Knowledge domain does not match the target Knowledge Pack");
        }
    }

    private boolean sameDomain(String articleCategory, String incomingDomain) {
        return normalizeDomain(articleCategory).equals(incomingDomain);
    }

    private String normalizeDomain(String category) {
        return safeCategory(category).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private String removeProcessArtifacts(String content) {
        return content
                .replaceAll("(?im)^.*Knowledge Candidate.*$", "")
                .replaceAll("(?im)^.*Title\\s*:.*$", "")
                .replaceAll("(?im)^.*Purpose\\s*:.*$", "")
                .replaceAll("(?im)^.*Source chunk.*$", "")
                .replaceAll("(?im)^.*Source document.*$", "")
                .replaceAll("(?im)^.*review checklist.*$", "")
                .replaceAll("(?im)^.*approval.*$", "")
                .replaceAll("(?im)^.*retrieval metadata.*$", "")
                .replaceAll("(?im)^.*(course|bootcamp|training|certification|enroll|signup|sign up|pricing|advertisement|sponsored).*$", "")
                .replaceAll("\\n{3,}", "\n\n");
    }

    private void validateKnowledge(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("Knowledge content cannot be blank");
        }
    }

    private void moveOverflow(Set<String> source, Set<String> target, int maxSize) {
        if (source.size() <= maxSize) {
            return;
        }
        List<String> ordered = new ArrayList<>(source);
        source.clear();
        for (int i = 0; i < ordered.size(); i++) {
            if (i < maxSize) {
                source.add(ordered.get(i));
            } else {
                target.add(ordered.get(i));
            }
        }
    }

    private KnowledgeArticle reload(KnowledgeArticle article) {
        KnowledgeArticle reloaded = knowledgeArticleMapper.findById(article.getId());
        return reloaded == null ? article : reloaded;
    }

    private record ArticleScore(KnowledgeArticle article, int score) {
    }

    private record SectionBuckets(List<String> coreConcepts,
                                  List<String> configuration,
                                  List<String> workflow,
                                  List<String> troubleshooting,
                                  List<String> bestPractices) {
    }
}
