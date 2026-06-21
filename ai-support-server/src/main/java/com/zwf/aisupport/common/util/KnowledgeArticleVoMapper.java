package com.zwf.aisupport.common.util;

import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.vo.KnowledgeArticleVO;

public final class KnowledgeArticleVoMapper {

    private KnowledgeArticleVoMapper() {
    }

    public static KnowledgeArticleVO toVO(KnowledgeArticle article) {
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
