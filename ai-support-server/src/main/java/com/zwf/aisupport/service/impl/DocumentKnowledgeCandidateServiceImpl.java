package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeArticleVoMapper;
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
    private final KnowledgePackReorganizer knowledgePackReorganizer;

    public DocumentKnowledgeCandidateServiceImpl(DocumentKnowledgeCandidateMapper candidateMapper,
                                                 KnowledgeArticleMapper knowledgeArticleMapper,
                                                 KnowledgePackReorganizer knowledgePackReorganizer) {
        this.candidateMapper = candidateMapper;
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
    }

    @Override
    public List<DocumentKnowledgeCandidateVO> listAll() {
        CurrentUserUtil.requireAdmin("Only administrators can manage document knowledge candidates");
        return candidateMapper.findAll();
    }

    @Override
    public KnowledgeArticleVO approveToArticle(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage document knowledge candidates");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();
        DocumentKnowledgeCandidate candidate = requireCandidate(id);
        if ("CONVERTED".equals(candidate.getStatus()) && candidate.getConvertedArticleId() != null) {
            KnowledgeArticle article = knowledgeArticleMapper.findById(candidate.getConvertedArticleId());
            if (article != null) {
                return KnowledgeArticleVoMapper.toVO(article);
            }
        }
        if ("REJECTED".equals(candidate.getStatus())) {
            throw new BusinessException("Rejected document knowledge cannot be converted to an article");
        }

        KnowledgeArticle article = candidate.getSuggestedArticleId() == null
                ? knowledgePackReorganizer.reorganizeOrCreate(
                candidate.getTitle(),
                candidate.getCategory(),
                candidate.getContent(),
                currentUserId,
                "Uploaded document knowledge candidate")
                : knowledgePackReorganizer.reorganizeExistingById(
                candidate.getSuggestedArticleId(),
                candidate.getTitle(),
                candidate.getCategory(),
                candidate.getContent(),
                "Uploaded document knowledge candidate");

        candidateMapper.markConverted(candidate.getId(), currentUserId, article.getId());
        return KnowledgeArticleVoMapper.toVO(article);
    }

    @Override
    public DocumentKnowledgeCandidateVO updateReviewContent(Long id, String title, String content, String category) {
        CurrentUserUtil.requireAdmin("Only administrators can manage document knowledge candidates");
        DocumentKnowledgeCandidate candidate = requireCandidate(id);
        if (!"NEEDS_REVIEW".equals(candidate.getStatus())) {
            throw new BusinessException("Only pending document knowledge can be edited before approval");
        }
        candidateMapper.updateReviewContent(id, title.trim(), content.trim(), category == null ? null : category.trim());
        return candidateMapper.findVOById(id);
    }

    @Override
    public void reject(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage document knowledge candidates");
        DocumentKnowledgeCandidate candidate = requireCandidate(id);
        if ("CONVERTED".equals(candidate.getStatus())) {
            throw new BusinessException("Converted document knowledge cannot be rejected");
        }
        candidateMapper.markRejected(candidate.getId(), CurrentUserUtil.requireCurrentUserId());
    }

    @Override
    public void archive(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage document knowledge candidates");
        DocumentKnowledgeCandidate candidate = requireCandidate(id);
        if ("CONVERTED".equals(candidate.getStatus())) {
            throw new BusinessException("Converted document knowledge is part of the knowledge audit trail and cannot be archived here");
        }
        candidateMapper.markArchived(candidate.getId(), CurrentUserUtil.requireCurrentUserId());
    }

    @Override
    public void delete(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can manage document knowledge candidates");
        DocumentKnowledgeCandidate candidate = requireCandidate(id);
        if (!"REJECTED".equals(candidate.getStatus()) && !"ARCHIVED".equals(candidate.getStatus())) {
            throw new BusinessException("Only rejected or archived document knowledge can be deleted");
        }
        candidateMapper.deleteById(candidate.getId());
    }

    private DocumentKnowledgeCandidate requireCandidate(Long id) {
        DocumentKnowledgeCandidate candidate = candidateMapper.findById(id);
        if (candidate == null) {
            throw new BusinessException("Document knowledge candidate not found");
        }
        return candidate;
    }

}
