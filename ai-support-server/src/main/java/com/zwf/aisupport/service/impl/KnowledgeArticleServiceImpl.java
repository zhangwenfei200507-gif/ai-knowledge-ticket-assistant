package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.service.KnowledgeArticleService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {

    private final KnowledgeArticleMapper knowledgeArticleMapper;

    public KnowledgeArticleServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
    }

    @Override
    public void create(KnowledgeArticleCreateDTO knowledgeArticleCreateDTO) {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can create knowledge articles");
        }

        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        KnowledgeArticle article = new KnowledgeArticle();
        article.setTitle(knowledgeArticleCreateDTO.getTitle());
        article.setContent(knowledgeArticleCreateDTO.getContent());
        article.setCategory(knowledgeArticleCreateDTO.getCategory());
        article.setCreatedBy(currentUserId);
        article.setStatus("PUBLISHED");

        knowledgeArticleMapper.insert(article);
    }

    @Override
    public List<KnowledgeArticleVO> listPublishedArticles() {
        return knowledgeArticleMapper.findPublishedArticles()
                .stream()
                .map(this::convertToKnowledgeArticleVO)
                .toList();
    }

    @Override
    public KnowledgeArticleVO getDetail(Long id) {
        KnowledgeArticle article = knowledgeArticleMapper.findById(id);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BusinessException("Knowledge article not found");
        }

        return convertToKnowledgeArticleVO(article);
    }

    private KnowledgeArticleVO convertToKnowledgeArticleVO(KnowledgeArticle article) {
        KnowledgeArticleVO knowledgeArticleVO = new KnowledgeArticleVO();
        knowledgeArticleVO.setId(article.getId());
        knowledgeArticleVO.setTitle(article.getTitle());
        knowledgeArticleVO.setContent(article.getContent());
        knowledgeArticleVO.setCategory(article.getCategory());
        knowledgeArticleVO.setCreatedBy(article.getCreatedBy());
        knowledgeArticleVO.setStatus(article.getStatus());
        knowledgeArticleVO.setCreatedTime(article.getCreatedTime());
        knowledgeArticleVO.setUpdatedTime(article.getUpdatedTime());
        return knowledgeArticleVO;
    }
    @Override
    public List<KnowledgeArticleVO> searchPublishedArticles(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listPublishedArticles();
        }

        // Keyword search is the first step before adding AI semantic retrieval.
        return knowledgeArticleMapper.searchPublishedArticles(keyword.trim())
                .stream()
                .map(this::convertToKnowledgeArticleVO)
                .toList();
    }
}