package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.config.AiRoutingProperties;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.vo.AiAnswerSourceVO;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LlmUsagePolicy {

    private static final Set<String> DIRECT_FALLBACK_DOMAIN_TERMS = Set.of(
            "cloudops", "kubernetes", "docker", "container", "postgres", "postgresql", "redis",
            "jwt", "token", "spring security", "oauth", "oidc", "login", "auth", "database",
            "cache", "incident", "ticket", "sla", "deployment", "pod", "ingress", "gateway",
            "runbook", "sop", "linux", "api", "timeout", "connection", "latency"
    );

    private final AiRoutingProperties properties;

    public LlmUsagePolicy(AiRoutingProperties properties) {
        this.properties = properties;
    }

    public boolean canUseGroundedSynthesis(String sourceType, List<AiAnswerSourceVO> sources) {
        return properties.isGroundedSynthesisEnabled()
                && isGroundedSourceTypeAllowed(sourceType)
                && sources != null
                && sources.stream().anyMatch(source -> source.getPreviewContent() != null && !source.getPreviewContent().isBlank());
    }

    public boolean canUseDirectFallback(String question) {
        return properties.isDirectFallbackEnabled()
                && question != null
                && !question.isBlank()
                && question.length() <= properties.getMaxQuestionCharsForDirectFallback()
                && isProjectDomainQuestion(question);
    }

    public List<AiAnswerSourceVO> selectSourcesForGroundedSynthesis(List<AiAnswerSourceVO> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .filter(source -> source.getPreviewContent() != null && !source.getPreviewContent().isBlank())
                .sorted(Comparator.comparingDouble(this::sourcePriority).reversed())
                .limit(properties.getMaxGroundedSources())
                .map(this::copyForLlm)
                .toList();
    }

    private AiAnswerSourceVO copyForLlm(AiAnswerSourceVO source) {
        AiAnswerSourceVO copy = new AiAnswerSourceVO();
        copy.setSourceType(source.getSourceType());
        copy.setSourceScore(source.getSourceScore());
        copy.setSourceArticleId(source.getSourceArticleId());
        copy.setSourceTitle(source.getSourceTitle());
        copy.setSourceDocumentId(source.getSourceDocumentId());
        copy.setSourceDocumentName(source.getSourceDocumentName());
        copy.setSourceChunkId(source.getSourceChunkId());
        copy.setSourceChunkIndex(source.getSourceChunkIndex());
        copy.setSourceWebEvidenceId(source.getSourceWebEvidenceId());
        copy.setSourceUrl(source.getSourceUrl());
        copy.setSourceDomain(source.getSourceDomain());
        copy.setSourceStatus(source.getSourceStatus());
        copy.setPreviewContent(TextUtil.limit(source.getPreviewContent(), properties.getMaxPreviewCharsPerSource()));
        return copy;
    }

    private double sourcePriority(AiAnswerSourceVO source) {
        double score = source.getSourceScore() == null ? 0.0 : source.getSourceScore();
        if ("ARTICLE".equals(source.getSourceType())) {
            score += 3.0;
        } else if ("DOCUMENT".equals(source.getSourceType())) {
            score += 2.0;
        } else if ("WEB".equals(source.getSourceType())) {
            score += 1.5;
        }
        if (source.getSourceChunkId() != null) {
            score += 0.8;
        }
        return score;
    }

    private boolean isGroundedSourceTypeAllowed(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return false;
        }
        Set<String> allowedTypes = List.of(properties.getGroundedSynthesisSourceTypes().split(","))
                .stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
        return allowedTypes.contains(sourceType.toUpperCase());
    }

    private boolean isProjectDomainQuestion(String question) {
        String normalized = question.toLowerCase();
        return DIRECT_FALLBACK_DOMAIN_TERMS.stream().anyMatch(normalized::contains);
    }
}
