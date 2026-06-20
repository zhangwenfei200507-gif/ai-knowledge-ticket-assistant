package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.entity.DocumentKnowledgeCandidate;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.mapper.DocumentKnowledgeCandidateMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.service.DocumentKnowledgeCandidateService;
import com.zwf.aisupport.vo.DocumentKnowledgeCandidateVO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DocumentKnowledgeCandidateServiceImpl implements DocumentKnowledgeCandidateService {

    private final DocumentKnowledgeCandidateMapper candidateMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;

    public DocumentKnowledgeCandidateServiceImpl(DocumentKnowledgeCandidateMapper candidateMapper,
                                                 KnowledgeArticleMapper knowledgeArticleMapper) {
        this.candidateMapper = candidateMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
    }

    @Override
    public List<DocumentKnowledgeCandidateVO> listAll() {
        ensureAdmin();
        return candidateMapper.findAll();
    }

    @Override
    public KnowledgeArticleVO approveToArticle(Long id) {
        ensureAdmin();
        Long currentUserId = requireCurrentUserId();
        DocumentKnowledgeCandidate candidate = requireCandidate(id);
        if ("CONVERTED".equals(candidate.getStatus()) && candidate.getConvertedArticleId() != null) {
            KnowledgeArticle article = knowledgeArticleMapper.findById(candidate.getConvertedArticleId());
            if (article != null) {
                return convertToKnowledgeArticleVO(article);
            }
        }
        if ("REJECTED".equals(candidate.getStatus())) {
            throw new BusinessException("Rejected document knowledge cannot be converted to an article");
        }

        KnowledgeArticle article;
        if (isReorganizeAction(candidate) && candidate.getSuggestedArticleId() != null) {
            article = reorganizeExistingKnowledgePack(candidate);
        } else {
            article = createNewArticle(candidate, currentUserId);
        }
        candidateMapper.markConverted(candidate.getId(), currentUserId, article.getId());

        KnowledgeArticle savedArticle = knowledgeArticleMapper.findById(article.getId());
        return convertToKnowledgeArticleVO(savedArticle == null ? article : savedArticle);
    }

    private KnowledgeArticle reorganizeExistingKnowledgePack(DocumentKnowledgeCandidate candidate) {
        KnowledgeArticle article = knowledgeArticleMapper.findById(candidate.getSuggestedArticleId());
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BusinessException("Suggested article is no longer available");
        }

        article.setContent(buildReorganizedKnowledgePackContent(article, candidate));
        knowledgeArticleMapper.update(article);
        return article;
    }

    private KnowledgeArticle createNewArticle(DocumentKnowledgeCandidate candidate, Long currentUserId) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(cleanCandidateTitle(candidate.getTitle()));
        article.setContent(buildNewKnowledgePackContent(candidate));
        article.setCategory(candidate.getCategory());
        article.setCreatedBy(currentUserId);
        article.setStatus("PUBLISHED");
        knowledgeArticleMapper.insert(article);
        return article;
    }

    private boolean isReorganizeAction(DocumentKnowledgeCandidate candidate) {
        String action = candidate.getSuggestedAction();
        return "REORGANIZE_KNOWLEDGE".equals(action) || "MERGE_ARTICLE".equals(action);
    }

    private String buildReorganizedKnowledgePackContent(KnowledgeArticle article, DocumentKnowledgeCandidate candidate) {
        String title = cleanArticleTitle(article.getTitle(), article.getContent());
        String category = article.getCategory() == null || article.getCategory().isBlank()
                ? safeCategory(candidate.getCategory())
                : article.getCategory();
        List<String> knowledgePoints = collectKnowledgePoints(article.getContent(), normalizeArticleContent(candidate));
        SectionBuckets buckets = organizeKnowledgePoints(knowledgePoints);

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(title).append("\n\n")
                .append("Category: ").append(category).append("\n\n")
                .append("## Overview\n")
                .append(buildOverview(category, title)).append("\n\n")
                .append("## Core Concepts\n")
                .append(formatSection(buckets.coreConcepts(), "- Keep the key concepts for this Knowledge Pack concise and non-duplicated.")).append("\n\n");

        if (!buckets.configuration().isEmpty()) {
            builder.append("## Configuration\n")
                    .append(formatSection(buckets.configuration(), "")).append("\n\n");
        }

        if (!buckets.workflow().isEmpty()) {
            builder.append("## Workflow\n")
                    .append(formatSection(buckets.workflow(), "")).append("\n\n");
        }

        builder.append("## Troubleshooting\n")
                .append(formatSection(buckets.troubleshooting(), "- Add concrete checks, logs, metrics, and owner-specific escalation paths when available.")).append("\n\n")
                .append("## Best Practices\n")
                .append(formatSection(buckets.bestPractices(), "- Keep this article as the durable source of truth and avoid duplicating the same concept in separate articles.")).append("\n\n")
                .append("## References\n")
                .append("- Knowledge source: uploaded document candidate reviewed by administrator.\n")
                .append("- Source document id: ").append(candidate.getDocumentId() == null ? "-" : candidate.getDocumentId()).append("\n")
                .append("- Source chunk id: ").append(candidate.getChunkId() == null ? "-" : candidate.getChunkId()).append("\n")
                .append("- Maintenance rule: update this Knowledge Pack and reorganize retrieval chunks when new related knowledge is approved.\n");

        return builder.toString().trim();
    }

    private String buildNewKnowledgePackContent(DocumentKnowledgeCandidate candidate) {
        String category = safeCategory(candidate.getCategory());
        String title = cleanCandidateTitle(candidate.getTitle());
        List<String> knowledgePoints = collectKnowledgePoints(normalizeArticleContent(candidate));
        SectionBuckets buckets = organizeKnowledgePoints(knowledgePoints);

        return """
                # %s

                Category: %s

                ## Overview
                %s

                ## Core Concepts
                %s

                ## Troubleshooting
                %s

                ## Best Practices
                %s

                ## References
                - Knowledge source: uploaded document candidate reviewed by administrator.
                - Source document id: %s
                - Source chunk id: %s
                - Maintenance rule: update this Knowledge Pack and reorganize retrieval chunks when new related knowledge is approved.
                """.formatted(
                title,
                category,
                buildOverview(category, title),
                formatSection(buckets.coreConcepts(), "- Add the durable concepts for this Knowledge Pack before expanding it."),
                formatSection(buckets.troubleshooting(), "- Add concrete checks, logs, metrics, and owner-specific escalation paths when available."),
                formatSection(buckets.bestPractices(), "- Keep this article as the durable source of truth and avoid duplicating the same concept in separate articles."),
                candidate.getDocumentId() == null ? "-" : candidate.getDocumentId(),
                candidate.getChunkId() == null ? "-" : candidate.getChunkId()
        ).trim();
    }

    private String normalizeArticleContent(DocumentKnowledgeCandidate candidate) {
        return candidate.getContent()
                .replaceFirst("(?is)^#\\s+.*?(\\R\\R|$)", "")
                .replaceAll("(?im)^Review checklist:\\s*$[\\s\\S]*$", "")
                .replaceAll("(?im)^Knowledge architecture checklist:\\s*$[\\s\\S]*$", "")
                .replaceAll("(?im)^Category\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source document\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source chunk\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Knowledge to reorganize\\s*:?\\s*$", "")
                .replaceAll("(?im)^Key operational knowledge\\s*:?\\s*$", "")
                .trim();
    }

    private String cleanCandidateTitle(String title) {
        String cleaned = title == null || title.isBlank() ? "Knowledge Pack" : title.trim();
        return cleaned
                .replaceFirst("^Document Insight:\\s*", "")
                .replaceFirst("^Knowledge Candidate:\\s*", "Knowledge Pack: ");
    }

    private String cleanArticleTitle(String title, String content) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }
        if (content != null) {
            for (String line : content.split("\\R+")) {
                if (line.startsWith("# ")) {
                    return line.substring(2).trim();
                }
            }
        }
        return "Knowledge Pack";
    }

    private List<String> collectKnowledgePoints(String... contents) {
        LinkedHashSet<String> points = new LinkedHashSet<>();
        for (String content : contents) {
            if (content == null || content.isBlank()) {
                continue;
            }
            String normalized = content
                    .replaceAll("(?im)^#+\\s*", "")
                    .replaceAll("(?im)^Category\\s*:\\s*.*$", "")
                    .replaceAll("(?im)^References\\s*$", "")
                    .replaceAll("(?im)^Maintenance rule\\s*:\\s*.*$", "");
            for (String raw : normalized.split("\\R+|(?<=[.!?。！？])\\s+")) {
                String point = normalizePoint(raw);
                if (isDurableKnowledgePoint(point)) {
                    points.add(point);
                }
            }
        }
        return new ArrayList<>(points);
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
                && !normalized.startsWith("source document")
                && !normalized.startsWith("source chunk")
                && !normalized.startsWith("knowledge source")
                && !normalized.contains("reviewed by administrator")
                && !normalized.contains("review checklist")
                && !normalized.contains("knowledge architecture checklist")
                && !normalized.contains("reorganize retrieval chunks")
                && !normalized.contains("source document id")
                && !normalized.contains("source chunk id");
    }

    private SectionBuckets organizeKnowledgePoints(List<String> points) {
        LinkedHashSet<String> core = new LinkedHashSet<>();
        LinkedHashSet<String> configuration = new LinkedHashSet<>();
        LinkedHashSet<String> workflow = new LinkedHashSet<>();
        LinkedHashSet<String> troubleshooting = new LinkedHashSet<>();
        LinkedHashSet<String> bestPractices = new LinkedHashSet<>();

        for (String point : points) {
            String normalized = point.toLowerCase(Locale.ROOT);
            if (containsAny(normalized, "configure", "configuration", "setting", "property", "env", "yaml", "port", "timeout", "token", "secret")) {
                configuration.add(point);
            } else if (containsAny(normalized, "workflow", "step", "process", "pipeline", "flow", "before", "after")) {
                workflow.add(point);
            } else if (containsAny(normalized, "error", "fail", "failure", "down", "timeout", "check", "verify", "diagnose", "log", "metric", "alert")) {
                troubleshooting.add(point);
            } else if (containsAny(normalized, "should", "must", "avoid", "recommend", "best practice", "prevent", "keep", "ensure")) {
                bestPractices.add(point);
            } else {
                core.add(point);
            }
        }

        moveOverflow(core, troubleshooting, 8);
        return new SectionBuckets(
                new ArrayList<>(core),
                new ArrayList<>(configuration),
                new ArrayList<>(workflow),
                new ArrayList<>(troubleshooting),
                new ArrayList<>(bestPractices)
        );
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

    private String buildOverview(String category, String title) {
        return "This Knowledge Pack covers " + safeCategory(category)
                + " knowledge for AegisCloud support. It is maintained as a durable, low-redundancy article for AI retrieval and administrator review.";
    }

    private String formatSection(List<String> points, String fallback) {
        if (points == null || points.isEmpty()) {
            return fallback == null || fallback.isBlank() ? "- Add reviewed knowledge for this section." : fallback;
        }
        StringBuilder builder = new StringBuilder();
        points.stream()
                .limit(10)
                .forEach(point -> builder.append("- ").append(limit(point, 260)).append("\n"));
        return builder.toString().trim();
    }

    private String safeCategory(String category) {
        return category == null || category.isBlank() ? "General" : category.trim();
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength).trim();
    }

    private record SectionBuckets(List<String> coreConcepts,
                                  List<String> configuration,
                                  List<String> workflow,
                                  List<String> troubleshooting,
                                  List<String> bestPractices) {
    }

    @Override
    public void reject(Long id) {
        ensureAdmin();
        DocumentKnowledgeCandidate candidate = requireCandidate(id);
        if ("CONVERTED".equals(candidate.getStatus())) {
            throw new BusinessException("Converted document knowledge cannot be rejected");
        }
        candidateMapper.markRejected(candidate.getId(), requireCurrentUserId());
    }

    private DocumentKnowledgeCandidate requireCandidate(Long id) {
        DocumentKnowledgeCandidate candidate = candidateMapper.findById(id);
        if (candidate == null) {
            throw new BusinessException("Document knowledge candidate not found");
        }
        return candidate;
    }

    private void ensureAdmin() {
        if (!"ADMIN".equals(CurrentUserUtil.getCurrentUserRole())) {
            throw new BusinessException("Only administrators can manage document knowledge candidates");
        }
    }

    private Long requireCurrentUserId() {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }
        return currentUserId;
    }

    private KnowledgeArticleVO convertToKnowledgeArticleVO(KnowledgeArticle article) {
        KnowledgeArticleVO vo = new KnowledgeArticleVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setCategory(article.getCategory());
        vo.setCreatedBy(article.getCreatedBy());
        vo.setStatus(article.getStatus());
        vo.setCreatedTime(article.getCreatedTime());
        vo.setUpdatedTime(article.getUpdatedTime());
        return vo;
    }
}
