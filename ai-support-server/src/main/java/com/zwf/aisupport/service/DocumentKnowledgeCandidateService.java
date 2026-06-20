package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.DocumentKnowledgeCandidateVO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;

import java.util.List;

public interface DocumentKnowledgeCandidateService {

    List<DocumentKnowledgeCandidateVO> listAll();

    KnowledgeArticleVO approveToArticle(Long id);

    void reject(Long id);
}
