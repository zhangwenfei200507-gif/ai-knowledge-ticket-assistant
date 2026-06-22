package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.ReviewContentUpdateDTO;
import com.zwf.aisupport.service.DocumentKnowledgeCandidateService;
import com.zwf.aisupport.vo.DocumentKnowledgeCandidateVO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/document-knowledge-candidates")
public class DocumentKnowledgeCandidateController {

    private final DocumentKnowledgeCandidateService candidateService;

    public DocumentKnowledgeCandidateController(DocumentKnowledgeCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    public Result<PageVO<DocumentKnowledgeCandidateVO>> listAll(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String category,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return Result.success(candidateService.listAll(keyword, status, category, page, size));
    }

    @PostMapping("/{id}/approve")
    public Result<KnowledgeArticleVO> approveToArticle(@PathVariable Long id) {
        return Result.success(candidateService.approveToArticle(id));
    }

    @PutMapping("/{id}")
    public Result<DocumentKnowledgeCandidateVO> updateReviewContent(@PathVariable Long id,
                                                                    @Valid @RequestBody ReviewContentUpdateDTO updateDTO) {
        return Result.success(candidateService.updateReviewContent(
                id,
                updateDTO.getTitle(),
                updateDTO.getContent(),
                updateDTO.getCategory()
        ));
    }

    @PatchMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        candidateService.reject(id);
        return Result.success();
    }

    @PatchMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        candidateService.archive(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        candidateService.delete(id);
        return Result.success();
    }
}
