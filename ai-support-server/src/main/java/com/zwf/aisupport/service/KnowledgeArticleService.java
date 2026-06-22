package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.dto.KnowledgeArticleUpdateDTO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.KnowledgeCategoryStatsVO;
import com.zwf.aisupport.vo.PageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeArticleService {

    void create(KnowledgeArticleCreateDTO knowledgeArticleCreateDTO);

    KnowledgeArticleVO upload(MultipartFile file, String category, String title);

    PageVO<KnowledgeArticleVO> listPublishedArticles(String keyword, String category, String status, Integer page, Integer size);

    KnowledgeArticleVO getDetail(Long id);

    List<KnowledgeArticleVO> searchPublishedArticles(String keyword);

    KnowledgeArticleVO update(Long id, KnowledgeArticleUpdateDTO updateDTO);

    void archive(Long id);

    List<KnowledgeCategoryStatsVO> getCategoryStats();
}
