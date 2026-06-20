package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.dto.WebEvidenceSearchDTO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.WebEvidenceMapper;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.WebEvidenceVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class WebEvidenceServiceImpl implements WebEvidenceService {

    private final WebSearchClient webSearchClient;
    private final WebEvidenceMapper webEvidenceMapper;
    private final KnowledgeArticleMapper knowledgeArticleMapper;

    public WebEvidenceServiceImpl(WebSearchClient webSearchClient,
                                  WebEvidenceMapper webEvidenceMapper,
                                  KnowledgeArticleMapper knowledgeArticleMapper) {
        this.webSearchClient = webSearchClient;
        this.webEvidenceMapper = webEvidenceMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
    }

    @Override
    public Optional<WebEvidence> searchAndStoreEvidence(String question, Long userId) {
        Optional<WebEvidence> evidence = webSearchClient.search(question, userId);
        evidence.ifPresent(webEvidenceMapper::insert);
        return evidence.map(savedEvidence -> {
            WebEvidence reloaded = webEvidenceMapper.findById(savedEvidence.getId());
            return reloaded == null ? savedEvidence : reloaded;
        });
    }

    @Override
    public WebEvidenceVO searchManually(WebEvidenceSearchDTO searchDTO) {
        ensureAdmin();
        Long currentUserId = requireCurrentUserId();
        WebEvidence evidence = searchAndStoreEvidence(searchDTO.getQuestion().trim(), currentUserId)
                .orElseThrow(() -> new BusinessException("No reliable external evidence was found for this question"));
        return convertToVO(evidence);
    }

    @Override
    public List<WebEvidenceVO> listAll() {
        ensureAdmin();
        return webEvidenceMapper.findAll().stream()
                .map(this::convertToVO)
                .toList();
    }

    @Override
    public KnowledgeArticleVO approveToArticle(Long id) {
        ensureAdmin();
        Long currentUserId = requireCurrentUserId();
        WebEvidence evidence = requireEvidence(id);
        if ("CONVERTED".equals(evidence.getStatus()) && evidence.getConvertedArticleId() != null) {
            KnowledgeArticle article = knowledgeArticleMapper.findById(evidence.getConvertedArticleId());
            if (article != null) {
                return convertToKnowledgeArticleVO(article);
            }
        }
        if ("REJECTED".equals(evidence.getStatus())) {
            throw new BusinessException("Rejected evidence cannot be converted to an article");
        }

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(evidence.getSuggestedArticleTitle());
        article.setContent(evidence.getSuggestedArticleContent());
        article.setCategory(evidence.getCategory());
        article.setCreatedBy(currentUserId);
        article.setStatus("PUBLISHED");
        knowledgeArticleMapper.insert(article);
        webEvidenceMapper.markConverted(evidence.getId(), currentUserId, article.getId());
        KnowledgeArticle savedArticle = knowledgeArticleMapper.findById(article.getId());
        return convertToKnowledgeArticleVO(savedArticle == null ? article : savedArticle);
    }

    @Override
    public void reject(Long id) {
        ensureAdmin();
        WebEvidence evidence = requireEvidence(id);
        if ("CONVERTED".equals(evidence.getStatus())) {
            throw new BusinessException("Converted evidence cannot be rejected");
        }
        webEvidenceMapper.markRejected(evidence.getId(), requireCurrentUserId());
    }

    private WebEvidence requireEvidence(Long id) {
        WebEvidence evidence = webEvidenceMapper.findById(id);
        if (evidence == null) {
            throw new BusinessException("Web evidence not found");
        }
        return evidence;
    }

    private void ensureAdmin() {
        if (!"ADMIN".equals(CurrentUserUtil.getCurrentUserRole())) {
            throw new BusinessException("Only administrators can manage web evidence");
        }
    }

    private Long requireCurrentUserId() {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }
        return currentUserId;
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
        vo.setStatus(evidence.getStatus());
        vo.setCreatedBy(evidence.getCreatedBy());
        vo.setReviewedBy(evidence.getReviewedBy());
        vo.setConvertedArticleId(evidence.getConvertedArticleId());
        vo.setCreatedTime(evidence.getCreatedTime());
        vo.setReviewedTime(evidence.getReviewedTime());
        return vo;
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
