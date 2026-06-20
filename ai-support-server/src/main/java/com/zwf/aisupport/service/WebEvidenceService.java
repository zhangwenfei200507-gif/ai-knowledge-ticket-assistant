package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.WebEvidenceSearchDTO;
import com.zwf.aisupport.entity.WebEvidence;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.WebEvidenceVO;

import java.util.List;
import java.util.Optional;

public interface WebEvidenceService {

    Optional<WebEvidence> searchAndStoreEvidence(String question, Long userId);

    WebEvidenceVO searchManually(WebEvidenceSearchDTO searchDTO);

    List<WebEvidenceVO> listAll();

    KnowledgeArticleVO approveToArticle(Long id);

    void reject(Long id);
}
