package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.WebEvidenceSearchDTO;
import com.zwf.aisupport.service.WebEvidenceService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.WebEvidenceVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/web-evidence")
public class WebEvidenceController {

    private final WebEvidenceService webEvidenceService;

    public WebEvidenceController(WebEvidenceService webEvidenceService) {
        this.webEvidenceService = webEvidenceService;
    }

    @GetMapping
    public Result<List<WebEvidenceVO>> listAll() {
        return Result.success(webEvidenceService.listAll());
    }

    @PostMapping("/search")
    public Result<WebEvidenceVO> search(@Valid @RequestBody WebEvidenceSearchDTO searchDTO) {
        return Result.success(webEvidenceService.searchManually(searchDTO));
    }

    @PostMapping("/{id}/approve")
    public Result<KnowledgeArticleVO> approveToArticle(@PathVariable Long id) {
        return Result.success(webEvidenceService.approveToArticle(id));
    }

    @PatchMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        webEvidenceService.reject(id);
        return Result.success();
    }
}
