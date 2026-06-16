package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.vo.DocumentChunkVO;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import com.zwf.aisupport.vo.KnowledgeDocumentVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public Result<KnowledgeDocumentVO> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "category", required = false) String category) {
        KnowledgeDocumentVO document = documentService.upload(file, category);
        return Result.success(document);
    }

    @GetMapping
    public Result<List<KnowledgeDocumentVO>> listDocuments() {
        List<KnowledgeDocumentVO> documents = documentService.listDocuments();
        return Result.success(documents);
    }
    @GetMapping("/chunks/search")
    public Result<List<DocumentSearchResultVO>> searchChunks(@RequestParam String keyword) {
        List<DocumentSearchResultVO> results = documentService.searchChunks(keyword);
        return Result.success(results);
    }
    @GetMapping("/{documentId}/chunks")
    public Result<List<DocumentChunkVO>> listChunks(@PathVariable Long documentId) {
        List<DocumentChunkVO> chunks = documentService.listChunks(documentId);
        return Result.success(chunks);
    }
}