package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.service.DocumentKnowledgeCandidateService;
import com.zwf.aisupport.vo.DocumentKnowledgeCandidateVO;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/document-knowledge-candidates")
public class DocumentKnowledgeCandidateController {

    private final DocumentKnowledgeCandidateService candidateService;

    public DocumentKnowledgeCandidateController(DocumentKnowledgeCandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @GetMapping
    public Result<List<DocumentKnowledgeCandidateVO>> listAll() {
        return Result.success(candidateService.listAll());
    }

    @PostMapping("/{id}/approve")
    public Result<KnowledgeArticleVO> approveToArticle(@PathVariable Long id) {
        return Result.success(candidateService.approveToArticle(id));
    }

    @PatchMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        candidateService.reject(id);
        return Result.success();
    }
}
