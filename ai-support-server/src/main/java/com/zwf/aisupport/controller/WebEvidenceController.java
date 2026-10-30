package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.ReviewContentUpdateDTO;
import com.zwf.aisupport.dto.WebEvidenceSearchDTO;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.PageVO;
import com.zwf.aisupport.vo.WebEvidenceVO;
import com.zwf.aisupport.vo.WebEvidenceSummaryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/web-evidence")
public class WebEvidenceController {

    private final WebEvidenceService webEvidenceService;

    public WebEvidenceController(WebEvidenceService webEvidenceService) {
        this.webEvidenceService = webEvidenceService;
    }

    @GetMapping
    public Result<PageVO<WebEvidenceVO>> listAll(@RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) String status,
                                                 @RequestParam(required = false) String category,
                                                 @RequestParam(required = false) String origin,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer size) {
        return Result.success(webEvidenceService.listAll(keyword, status, category, origin, page, size));
    }

    @GetMapping("/summary")
    public Result<WebEvidenceSummaryVO> summary() {
        return Result.success(webEvidenceService.getSummary());
    }

    @PostMapping("/search")
    public Result<WebEvidenceVO> search(@Valid @RequestBody WebEvidenceSearchDTO searchDTO) {
        return Result.success(webEvidenceService.searchManually(searchDTO));
    }

    @PostMapping("/{id}/approve")
    public Result<KnowledgeArticleVO> approveToArticle(@PathVariable Long id) {
        return Result.success(webEvidenceService.approveToArticle(id));
    }

    @PutMapping("/{id}/suggested-article")
    public Result<WebEvidenceVO> updateSuggestedArticle(@PathVariable Long id,
                                                        @Valid @RequestBody ReviewContentUpdateDTO updateDTO) {
        return Result.success(webEvidenceService.updateSuggestedArticle(
                id,
                updateDTO.getTitle(),
                updateDTO.getContent()
        ));
    }

    @PatchMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        webEvidenceService.reject(id);
        return Result.success();
    }

    @PatchMapping("/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        webEvidenceService.archive(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        webEvidenceService.delete(id);
        return Result.success();
    }
}
