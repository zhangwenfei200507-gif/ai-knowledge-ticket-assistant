package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.KnowledgeArticleVoMapper;
import com.zwf.aisupport.common.util.UploadedTextExtractor;
import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.dto.KnowledgeArticleUpdateDTO;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.service.KnowledgeArticleService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.KnowledgeCategoryStatsVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {

    private static final long MAX_ARTICLE_FILE_SIZE = 10 * 1024 * 1024;

    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgePackReorganizer knowledgePackReorganizer;

    public KnowledgeArticleServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper,
                                       KnowledgePackReorganizer knowledgePackReorganizer) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
    }

    @Override
    public void create(KnowledgeArticleCreateDTO knowledgeArticleCreateDTO) {
        CurrentUserUtil.requireAdmin("Only administrators can create knowledge articles");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        knowledgePackReorganizer.reorganizeOrCreate(
                knowledgeArticleCreateDTO.getTitle(),
                knowledgeArticleCreateDTO.getCategory(),
                knowledgeArticleCreateDTO.getContent(),
                currentUserId,
                "Manual administrator knowledge entry"
        );
    }

    @Override
    public KnowledgeArticleVO upload(MultipartFile file, String category, String title) {
        CurrentUserUtil.requireAdmin("Only administrators can upload knowledge articles");
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        UploadedTextExtractor.ExtractedText extractedText = UploadedTextExtractor.extract(file, MAX_ARTICLE_FILE_SIZE, "article");

        KnowledgeArticle article = knowledgePackReorganizer.reorganizeOrCreate(
                UploadedTextExtractor.titleFromFilename(extractedText.filename(), title),
                category,
                extractedText.content(),
                currentUserId,
                "Administrator uploaded article file: " + extractedText.filename()
        );
        return KnowledgeArticleVoMapper.toVO(article);
    }

    @Override
    public List<KnowledgeArticleVO> listPublishedArticles() {
        return knowledgeArticleMapper.findPublishedArticles()
                .stream()
                .map(KnowledgeArticleVoMapper::toVO)
                .toList();
    }

    @Override
    public KnowledgeArticleVO getDetail(Long id) {
        KnowledgeArticle article = knowledgeArticleMapper.findById(id);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BusinessException("Knowledge article not found");
        }

        return KnowledgeArticleVoMapper.toVO(article);
    }
    @Override
    public List<KnowledgeArticleVO> searchPublishedArticles(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listPublishedArticles();
        }

        // Keyword search is the first step before adding AI semantic retrieval.
        return knowledgeArticleMapper.searchPublishedArticles(keyword.trim())
                .stream()
                .map(KnowledgeArticleVoMapper::toVO)
                .toList();
    }
    @Override
    public KnowledgeArticleVO update(Long id, KnowledgeArticleUpdateDTO updateDTO) {
        CurrentUserUtil.requireAdmin("Only administrators can update knowledge articles");

        KnowledgeArticle article = knowledgeArticleMapper.findById(id);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BusinessException("Knowledge article not found");
        }

        KnowledgeArticle updatedArticle = knowledgePackReorganizer.rewriteSingleArticle(
                id,
                updateDTO.getTitle(),
                updateDTO.getCategory(),
                updateDTO.getContent(),
                "Manual administrator Knowledge Pack edit"
        );
        return KnowledgeArticleVoMapper.toVO(updatedArticle);
    }

    @Override
    public void archive(Long id) {
        CurrentUserUtil.requireAdmin("Only administrators can archive knowledge articles");

        KnowledgeArticle article = knowledgeArticleMapper.findById(id);
        if (article == null || !"PUBLISHED".equals(article.getStatus())) {
            throw new BusinessException("Knowledge article not found");
        }

        // Archive outdated knowledge instead of deleting it permanently.
        knowledgeArticleMapper.archive(id);
    }
    @Override
    public List<KnowledgeCategoryStatsVO> getCategoryStats() {
        CurrentUserUtil.requireAdmin("Only administrators can access knowledge category stats");

        // Category stats help administrators understand how the knowledge base is organized.
        return knowledgeArticleMapper.countPublishedArticlesByCategory();
    }

}
