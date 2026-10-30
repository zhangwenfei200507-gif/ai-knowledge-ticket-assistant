package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.WebEvidenceSearchDTO;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.PageVO;
import com.zwf.aisupport.vo.WebEvidenceVO;
import com.zwf.aisupport.vo.WebEvidenceSummaryVO;

import java.util.List;
import java.util.Optional;

public interface WebEvidenceService {

    Optional<WebEvidence> searchAndStoreEvidence(String question, Long userId);

    WebEvidence storeModelGeneratedEvidence(String question, String answer, Long userId);

    WebEvidenceVO updateSuggestedArticle(Long id, String suggestedArticleTitle, String suggestedArticleContent);

    WebEvidenceVO searchManually(WebEvidenceSearchDTO searchDTO);

    PageVO<WebEvidenceVO> listAll(String keyword, String status, String category, String origin, Integer page, Integer size);

    WebEvidenceSummaryVO getSummary();

    KnowledgeArticleVO approveToArticle(Long id);

    void reject(Long id);

    void archive(Long id);

    void delete(Long id);
}
