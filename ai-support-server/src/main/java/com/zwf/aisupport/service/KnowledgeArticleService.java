package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;

import java.util.List;

public interface KnowledgeArticleService {

    void create(KnowledgeArticleCreateDTO knowledgeArticleCreateDTO);

    List<KnowledgeArticleVO> listPublishedArticles();

    KnowledgeArticleVO getDetail(Long id);
}