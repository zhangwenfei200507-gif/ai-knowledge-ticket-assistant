package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.DocumentChunkUpdateDTO;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.vo.DocumentChunkAdminVO;
import com.zwf.aisupport.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/chunks")
public class AdminDocumentChunkController {

    private final DocumentService documentService;

    public AdminDocumentChunkController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public Result<PageVO<DocumentChunkAdminVO>> listChunks(@RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Long documentId,
                                                           @RequestParam(required = false) String category,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(required = false) Integer page,
                                                           @RequestParam(required = false) Integer size) {
        return Result.success(documentService.listChunksForAdmin(keyword, documentId, category, status, page, size));
    }

    @PutMapping("/{chunkId}")
    public Result<DocumentChunkAdminVO> updateChunk(@PathVariable Long chunkId,
                                                    @Valid @RequestBody DocumentChunkUpdateDTO updateDTO) {
        return Result.success(documentService.updateChunk(chunkId, updateDTO));
    }

    @PatchMapping("/{chunkId}/archive")
    public Result<Void> archiveChunk(@PathVariable Long chunkId) {
        documentService.archiveChunk(chunkId);
        return Result.success();
    }
}
