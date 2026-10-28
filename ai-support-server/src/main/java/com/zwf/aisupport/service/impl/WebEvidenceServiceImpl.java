package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeArticleVoMapper;
import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.common.util.PageRequestUtil;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.dto.WebEvidenceSearchDTO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.entity.WebEvidenceSource;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.WebEvidenceMapper;
import com.zwf.aisupport.mapper.WebEvidenceSourceMapper;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.PageVO;
import com.zwf.aisupport.vo.WebEvidenceVO;
import com.zwf.aisupport.vo.WebEvidenceSourceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class WebEvidenceServiceImpl implements WebEvidenceService {

    private final WebSearchClient webSearchClient;
    private final WebEvidenceMapper webEvidenceMapper;
    private final WebEvidenceSourceMapper webEvidenceSourceMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgePackReorganizer knowledgePackReorganizer;
    private final KnowledgeArticleChunkManager articleChunkManager;

    public WebEvidenceServiceImpl(WebSearchClient webSearchClient,
                                  WebEvidenceMapper webEvidenceMapper,
                                  WebEvidenceSourceMapper webEvidenceSourceMapper,
                                  KnowledgeArticleMapper knowledgeArticleMapper,
                                  KnowledgePackReorganizer knowledgePackReorganizer,
                                  KnowledgeArticleChunkManager articleChunkManager) {
        this.webSearchClient = webSearchClient;
        this.webEvidenceMapper = webEvidenceMapper;
        this.webEvidenceSourceMapper = webEvidenceSourceMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
        this.articleChunkManager = articleChunkManager;
    }

    @Override
    public Optional<WebEvidence> searchAndStoreEvidence(String question, Long userId) {
        Optional<WebEvidence> evidence = webSearchClient.search(question, userId);
        if (evidence.isEmpty()) {
            return Optional.empty();
        }
        WebEvidence savedEvidence = evidence.get();
        WebEvidence existing = webEvidenceMapper.findActiveByQuestionAndUrl(savedEvidence.getQuestion(), savedEvidence.getUrl());
        if (existing != null) {
            return Optional.of(reloadWithSources(existing));
        }
        webEvidenceMapper.insert(savedEvidence);
        persistSources(savedEvidence);
        return Optional.of(reloadWithSources(savedEvidence));
    }

    @Override
    public WebEvidence storeModelGeneratedEvidence(String question, String answer, Long userId) {
        WebEvidence evidence = new WebEvidence();
        evidence.setQuestion(question);
        evidence.setQuery(question);
        evidence.setTitle(TextUtil.limit("Model Answer: " + question, 300));
        evidence.setUrl(null);
        evidence.setDomain("OpenAI model");
        evidence.setSnippet(TextUtil.limit(answer, 1200));
        evidence.setExtractedContent(answer);
        evidence.setSuggestedArticleTitle(TextUtil.limit(buildModelTitle(question, evidence.getCategory()), 150));
        evidence.setSuggestedArticleContent(buildModelSuggestedArticle(question, answer));
        evidence.setCategory(KnowledgeCategoryResolver.infer(question + " " + answer));
        // A model-only answer has no external source quality score. Keep it unknown
        // so the review UI cannot mistake an unverified answer for web evidence.
        evidence.setCredibilityScore(null);
        evidence.setEvidenceOrigin("LLM_GENERATED");
        evidence.setStatus("NEEDS_REVIEW");
        evidence.setCreatedBy(userId);
        webEvidenceMapper.insert(evidence);
        return reloadWithSources(evidence);
    }

    @Override
    public WebEvidenceVO updateSuggestedArticle(Long id, String suggestedArticleTitle, String suggestedArticleContent) {
        CurrentUserUtil.requireAdmin("Only administrators can manage web evidence");
        WebEvidence evidence = requireEvidence(id);
        if (!"NEEDS_REVIEW".equals(evidence.getStatus())) {
            throw new BusinessException("Only pending evidence can be edited before approval");
        }
        webEvidenceMapper.updateSuggestedArticle(
                id,
                suggestedArticleTitle == null || suggestedArticleTitle.isBlank()
                        ? evidence.getSuggestedArticleTitle()
                        : suggestedArticleTitle,
                suggestedArticleContent == null || suggestedArticleContent.isBlank()
                        ? evidence.getSuggestedArticleContent()
                        : suggestedArticleContent
        );
        WebEvidence updated = findWithSources(id);
        return convertToVO(updated == null ? evidence : updated);
    }

    @Override
    public WebEvidenceVO searchManually(WebEvidenceSearchDTO searchDTO) {
        CurrentUserUtil.requireAdmin("Only administrators can manage web evidence");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        WebEvidence evidence = searchAndStoreEvidence(searchDTO.getQuestion().trim(), currentUserId)
                .orElseThrow(() -> new BusinessException("No reliable external evidence was found for this question"));
        return convertToVO(evidence);
    }

    @Override
    public PageVO<WebEvidenceVO> listAll(String keyword, String status, String category, String origin, Integer page, Integer size) {
        CurrentUserUtil.requireAdmin("Only administrators can manage web evidence");
        int safePage = PageRequestUtil.page(page);
        int safeSize = PageRequestUtil.size(size);
        List<WebEvidence> evidencePage = webEvidenceMapper.findPage(
                        PageRequestUtil.optionalText(keyword),
                        PageRequestUtil.optionalText(status),
                        PageRequestUtil.optionalText(category),
                        PageRequestUtil.optionalText(origin),
                        PageRequestUtil.offset(safePage, safeSize),
                        safeSize
                );
        attachSources(evidencePage);
        List<WebEvidenceVO> items = evidencePage.stream()
                .map(this::convertToVO)
                .toList();
        long total = webEvidenceMapper.countPage(
                PageRequestUtil.optionalText(keyword),
                PageRequestUtil.optionalText(status),
                PageRequestUtil.optionalText(category),
                PageRequestUtil.optionalText(origin)
        );
        return PageVO.of(items, safePage, safeSize, total);
    }

    @Override
    @Transactional
    public KnowledgeArticleVO approveToArticle(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage web evidence");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        WebEvidence evidence = requireEvidence(id);
        if ("CONVERTED".equals(evidence.getStatus()) && evidence.getConvertedArticleId() != null) {
            KnowledgeArticle article = knowledgeArticleMapper.findById(evidence.getConvertedArticleId());
            if (article != null) {
                return KnowledgeArticleVoMapper.toVO(article);
            }
        }
        if ("REJECTED".equals(evidence.getStatus())) {
            throw new BusinessException("Rejected evidence cannot be converted to an article");
        }
        if (!"NEEDS_REVIEW".equals(evidence.getStatus())) {
            throw new BusinessException("Only pending evidence can be approved");
        }
        if (isLowQualityKnowledgeContent(evidence.getSuggestedArticleContent())) {
            throw new BusinessException("Evidence is not useful enough to publish as internal knowledge");
        }

        KnowledgeArticle article = knowledgePackReorganizer.reorganizeOrCreate(
                evidence.getSuggestedArticleTitle(),
                evidence.getCategory(),
                evidence.getSuggestedArticleContent(),
                currentUserId,
                "Reviewed " + (evidence.getEvidenceOrigin() == null ? "external evidence" : evidence.getEvidenceOrigin())
        );
        articleChunkManager.rebuildArticleChunks(article);
        webEvidenceMapper.markConverted(evidence.getId(), currentUserId, article.getId());
        return KnowledgeArticleVoMapper.toVO(article);
    }

    @Override
    public void reject(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage web evidence");
        WebEvidence evidence = requireEvidence(id);
        if (!"NEEDS_REVIEW".equals(evidence.getStatus())) {
            throw new BusinessException("Only pending evidence can be rejected");
        }
        webEvidenceMapper.markRejected(evidence.getId(), CurrentUserUtil.requireCurrentUserId());
    }

    @Override
    public void archive(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage web evidence");
        WebEvidence evidence = requireEvidence(id);
        if ("CONVERTED".equals(evidence.getStatus())) {
            throw new BusinessException("Converted evidence is part of the knowledge audit trail and cannot be archived here");
        }
        webEvidenceMapper.markArchived(evidence.getId(), CurrentUserUtil.requireCurrentUserId());
    }

    @Override
    public void delete(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage web evidence");
        WebEvidence evidence = requireEvidence(id);
        if (!"REJECTED".equals(evidence.getStatus()) && !"ARCHIVED".equals(evidence.getStatus())) {
            throw new BusinessException("Only rejected or archived evidence can be deleted");
        }
        webEvidenceMapper.deleteById(evidence.getId());
    }

    private WebEvidence requireEvidence(Long id) {
        WebEvidence evidence = findWithSources(id);
        if (evidence == null) {
            throw new BusinessException("Web evidence not found");
        }
        return evidence;
    }

    private WebEvidenceVO convertToVO(WebEvidence evidence) {
        WebEvidenceVO vo = new WebEvidenceVO();
        vo.setId(evidence.getId());
        vo.setQuestion(evidence.getQuestion());
        vo.setQuery(evidence.getQuery());
        vo.setTitle(evidence.getTitle());
        vo.setUrl(evidence.getUrl());
        vo.setDomain(evidence.getDomain());
        vo.setSnippet(evidence.getSnippet());
        vo.setExtractedContent(evidence.getExtractedContent());
        vo.setSuggestedArticleTitle(evidence.getSuggestedArticleTitle());
        vo.setSuggestedArticleContent(evidence.getSuggestedArticleContent());
        vo.setCategory(evidence.getCategory());
        vo.setCredibilityScore(evidence.getCredibilityScore());
        vo.setEvidenceOrigin(evidence.getEvidenceOrigin());
        vo.setStatus(evidence.getStatus());
        vo.setCreatedBy(evidence.getCreatedBy());
        vo.setReviewedBy(evidence.getReviewedBy());
        vo.setConvertedArticleId(evidence.getConvertedArticleId());
        vo.setCreatedTime(evidence.getCreatedTime());
        vo.setReviewedTime(evidence.getReviewedTime());
        vo.setSources(Optional.ofNullable(evidence.getSources()).orElse(List.of()).stream()
                .map(this::convertSourceToVO)
                .toList());
        return vo;
    }

    private void persistSources(WebEvidence evidence) {
        if (evidence.getId() == null || evidence.getSources() == null) {
            return;
        }
        for (WebEvidenceSource source : evidence.getSources()) {
            source.setEvidenceId(evidence.getId());
            webEvidenceSourceMapper.insert(source);
        }
    }

    private WebEvidence reloadWithSources(WebEvidence fallback) {
        WebEvidence reloaded = findWithSources(fallback.getId());
        return reloaded == null ? fallback : reloaded;
    }

    private WebEvidence findWithSources(Long id) {
        WebEvidence evidence = webEvidenceMapper.findById(id);
        if (evidence != null) {
            evidence.setSources(webEvidenceSourceMapper.findByEvidenceId(id));
        }
        return evidence;
    }

    private void attachSources(List<WebEvidence> evidence) {
        List<Long> ids = evidence.stream().map(WebEvidence::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, List<WebEvidenceSource>> byEvidenceId = webEvidenceSourceMapper.findByEvidenceIds(ids).stream()
                .collect(Collectors.groupingBy(WebEvidenceSource::getEvidenceId));
        evidence.forEach(item -> item.setSources(byEvidenceId.getOrDefault(item.getId(), List.of())));
    }

    private WebEvidenceSourceVO convertSourceToVO(WebEvidenceSource source) {
        WebEvidenceSourceVO vo = new WebEvidenceSourceVO();
        vo.setId(source.getId());
        vo.setTitle(source.getTitle());
        vo.setUrl(source.getUrl());
        vo.setDomain(source.getDomain());
        vo.setExcerpt(source.getExcerpt());
        vo.setCredibilityScore(source.getCredibilityScore());
        vo.setSourceRank(source.getSourceRank());
        vo.setSourceType(source.getSourceType());
        vo.setOfficialSource(source.getOfficialSource());
        vo.setPublishedTime(source.getPublishedTime());
        vo.setRetrievedTime(source.getRetrievedTime());
        vo.setSourceStatus(source.getSourceStatus());
        return vo;
    }

    private String buildModelSuggestedArticle(String question, String answer) {
        String category = KnowledgeCategoryResolver.infer(question + " " + answer);
        return """
                # %s

                Category: %s

                ## Overview
                %s
                """.formatted(TextUtil.limit(question, 120), category, normalizeArticleBullets(answer));
    }

    private String buildModelTitle(String question, String category) {
        String focus = question == null ? "Operational Knowledge" : question
                .replaceFirst("(?i)^\\s*(what|why|how|when|where|which|can|could|should|does|do|is|are)\\b", "")
                .replaceAll("[?!.]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return (category == null || category.isBlank() ? "CloudOps" : category)
                + ": " + TextUtil.limit(focus.isBlank() ? "Operational Knowledge" : focus, 105);
    }

    private String normalizeArticleBullets(String answer) {
        if (answer == null || answer.isBlank()) {
            return "- Add the reviewed technical answer before publishing.";
        }
        List<String> points = answer.lines()
                .map(String::trim)
                .filter(line -> line.length() >= 12)
                .map(line -> line.replaceAll("^[-*]\\s*", ""))
                .limit(6)
                .map(line -> "- " + TextUtil.limit(line, 260))
                .toList();
        if (points.isEmpty()) {
            return "- " + TextUtil.limit(answer.replaceAll("\\s+", " ").trim(), 260);
        }
        return String.join("\n", points);
    }

    private boolean isLowQualityKnowledgeContent(String content) {
        String normalized = content == null ? "" : content.toLowerCase();
        return normalized.contains("i could not find a reliable answer")
                || normalized.contains("i do not have enough reliable knowledge")
                || normalized.contains("add concrete checks, commands, dashboards, or logs")
                || normalized.contains("add the final reviewed answer here before publishing");
    }
}
