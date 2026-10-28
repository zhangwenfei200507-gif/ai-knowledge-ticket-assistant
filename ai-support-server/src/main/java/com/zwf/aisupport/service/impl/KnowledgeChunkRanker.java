package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.vo.KnowledgeArticleChunkSearchResultVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ranks semantic article chunks after the database retrieval step.
 * Database search remains responsible for candidate recall; this component
 * is responsible for intent, coverage, section fit, and domain-balanced ranking.
 */
@Component
public class KnowledgeChunkRanker {

    private final KnowledgeCoverageEvaluator coverageEvaluator;

    public KnowledgeChunkRanker(KnowledgeCoverageEvaluator coverageEvaluator) {
        this.coverageEvaluator = coverageEvaluator;
    }

    public List<KnowledgeArticleChunkSearchResultVO> select(
            Map<Long, List<KnowledgeArticleChunkSearchResultVO>> byArticle,
            AiQueryPlan plan) {
        if (byArticle == null || byArticle.isEmpty()) {
            return List.of();
        }
        if (plan.domainCandidates().size() > 1) {
            return selectAcrossDomains(byArticle, plan);
        }
        return byArticle.values().stream()
                .max(Comparator.comparingInt(group -> scoreGroup(group, plan)))
                .orElse(List.of())
                .stream()
                .sorted(Comparator.comparingInt((KnowledgeArticleChunkSearchResultVO result) -> score(result, plan)).reversed())
                .limit(5)
                .toList();
    }

    public int score(KnowledgeArticleChunkSearchResultVO result, AiQueryPlan plan) {
        if (result == null) {
            return Integer.MIN_VALUE;
        }
        String text = TextUtil.safeLower(result.getSectionTitle() + " "
                + result.getSectionType() + " "
                + result.getConceptTags() + " "
                + result.getContent());
        int score = result.getRelevanceScore() == null
                ? 0
                : (int) Math.round(result.getRelevanceScore() * 100);
        KnowledgeCoverageEvaluator.Coverage coverage = coverageEvaluator.evaluate(text, plan);
        score += (int) Math.round(coverage.score() * 80);
        score += coverage.coveredConcepts().size() * 10;
        if (sectionMatchesIntent(result.getSectionType(), plan.intent())) {
            score += 16;
        }
        if (TextUtil.containsAny(text, "knowledge pack covers", "add verified", "review before publishing", "source references")) {
            score -= 40;
        }
        return score;
    }

    private int scoreGroup(List<KnowledgeArticleChunkSearchResultVO> chunks, AiQueryPlan plan) {
        String combinedText = chunks.stream()
                .map(chunk -> chunk.getSectionTitle() + " " + chunk.getConceptTags() + " " + chunk.getContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        KnowledgeCoverageEvaluator.Coverage coverage = coverageEvaluator.evaluate(combinedText, plan);
        int retrievalScore = chunks.stream()
                .mapToInt(chunk -> score(chunk, plan))
                .max()
                .orElse(0);
        return (int) Math.round(coverage.score() * 240)
                + coverage.coveredConcepts().size() * 20
                - coverage.missingAnchors().size() * 120
                + retrievalScore;
    }

    private List<KnowledgeArticleChunkSearchResultVO> selectAcrossDomains(
            Map<Long, List<KnowledgeArticleChunkSearchResultVO>> byArticle,
            AiQueryPlan plan) {
        List<KnowledgeArticleChunkSearchResultVO> selected = new ArrayList<>();
        for (String domain : plan.domainCandidates()) {
            byArticle.values().stream()
                    .filter(group -> group.stream().anyMatch(chunk -> domain.equalsIgnoreCase(chunk.getCategory())))
                    .max(Comparator.comparingInt(group -> scoreGroup(group, plan)))
                    .ifPresent(group -> group.stream()
                            .sorted(Comparator.comparingInt((KnowledgeArticleChunkSearchResultVO result) -> score(result, plan)).reversed())
                            .limit(3)
                            .forEach(selected::add));
        }
        return selected.stream()
                .sorted(Comparator.comparingInt((KnowledgeArticleChunkSearchResultVO result) -> score(result, plan)).reversed())
                .limit(8)
                .toList();
    }

    private boolean sectionMatchesIntent(String sectionType, AiAnswerIntent intent) {
        String normalized = TextUtil.safeLower(sectionType);
        return switch (intent) {
            case TROUBLESHOOTING -> normalized.contains("troubleshooting");
            case PROCEDURE -> normalized.contains("workflow") || normalized.contains("configuration");
            case DECISION -> normalized.contains("best") || normalized.contains("concept");
            case CONCEPT -> normalized.contains("concept") || normalized.contains("configuration") || normalized.contains("best");
        };
    }
}
