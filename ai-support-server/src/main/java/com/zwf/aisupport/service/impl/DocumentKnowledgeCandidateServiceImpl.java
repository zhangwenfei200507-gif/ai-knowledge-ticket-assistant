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

import java.util.List;

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

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(candidate.getTitle());
        article.setContent(candidate.getContent());
        article.setCategory(candidate.getCategory());
        article.setCreatedBy(currentUserId);
        article.setStatus("PUBLISHED");
        knowledgeArticleMapper.insert(article);
        candidateMapper.markConverted(candidate.getId(), currentUserId, article.getId());

        KnowledgeArticle savedArticle = knowledgeArticleMapper.findById(article.getId());
        return convertToKnowledgeArticleVO(savedArticle == null ? article : savedArticle);
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
