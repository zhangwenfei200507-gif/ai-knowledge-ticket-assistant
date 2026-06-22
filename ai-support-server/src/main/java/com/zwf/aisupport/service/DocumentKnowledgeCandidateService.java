package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.DocumentKnowledgeCandidateVO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.PageVO;

import java.util.List;

public interface DocumentKnowledgeCandidateService {

    PageVO<DocumentKnowledgeCandidateVO> listAll(String keyword, String status, String category, Integer page, Integer size);

    KnowledgeArticleVO approveToArticle(Long id);

    DocumentKnowledgeCandidateVO updateReviewContent(Long id, String title, String content, String category);

    void reject(Long id);

    void archive(Long id);

    void delete(Long id);
}
