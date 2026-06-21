package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.DocumentKnowledgeCandidateVO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;

import java.util.List;

public interface DocumentKnowledgeCandidateService {

    List<DocumentKnowledgeCandidateVO> listAll();

    KnowledgeArticleVO approveToArticle(Long id);

    DocumentKnowledgeCandidateVO updateReviewContent(Long id, String title, String content, String category);

    void reject(Long id);

    void archive(Long id);

    void delete(Long id);
}
