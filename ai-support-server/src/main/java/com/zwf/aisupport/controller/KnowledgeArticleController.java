package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.dto.KnowledgeArticleUpdateDTO;
import com.zwf.aisupport.service.KnowledgeArticleService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.KnowledgeCategoryStatsVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/articles")
public class KnowledgeArticleController {

    private final KnowledgeArticleService knowledgeArticleService;

    public KnowledgeArticleController(KnowledgeArticleService knowledgeArticleService) {
        this.knowledgeArticleService = knowledgeArticleService;
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody KnowledgeArticleCreateDTO knowledgeArticleCreateDTO) {
        knowledgeArticleService.create(knowledgeArticleCreateDTO);
        return Result.success();
    }

    @GetMapping
    public Result<List<KnowledgeArticleVO>> listPublishedArticles() {
        List<KnowledgeArticleVO> articles = knowledgeArticleService.listPublishedArticles();
        return Result.success(articles);
    }
    @GetMapping("/search")
    public Result<List<KnowledgeArticleVO>> searchPublishedArticles(@RequestParam String keyword) {
        List<KnowledgeArticleVO> articles = knowledgeArticleService.searchPublishedArticles(keyword);
        return Result.success(articles);
    }
    @GetMapping("/categories/stats")
    public Result<List<KnowledgeCategoryStatsVO>> getCategoryStats() {
        List<KnowledgeCategoryStatsVO> stats = knowledgeArticleService.getCategoryStats();
        return Result.success(stats);
    }
    @GetMapping("/{id}")
    public Result<KnowledgeArticleVO> getDetail(@PathVariable Long id) {
        KnowledgeArticleVO article = knowledgeArticleService.getDetail(id);
        return Result.success(article);
    }
    @PutMapping("/{id}")
    public Result<KnowledgeArticleVO> update(@PathVariable Long id,
                                             @Valid @RequestBody KnowledgeArticleUpdateDTO updateDTO) {
        KnowledgeArticleVO article = knowledgeArticleService.update(id, updateDTO);
        return Result.success(article);
    }

    @PatchMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        knowledgeArticleService.archive(id);
        return Result.success();
    }

}