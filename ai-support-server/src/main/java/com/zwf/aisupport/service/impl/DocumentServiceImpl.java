package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.UploadedTextExtractor;
import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.entity.KnowledgeDocument;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeDocumentMapper;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.vo.DocumentCategoryStatsVO;
import com.zwf.aisupport.vo.DocumentChunkVO;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import com.zwf.aisupport.vo.KnowledgeDocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_CHUNK_LENGTH = 800;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;

    public DocumentServiceImpl(KnowledgeDocumentMapper knowledgeDocumentMapper,
                               DocumentChunkMapper documentChunkMapper) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
    }

    @Override
    public KnowledgeDocumentVO upload(MultipartFile file, String category) {
        Long currentUserId = CurrentUserUtil.getCurrentUserId();
        if (currentUserId == null) {
            throw new BusinessException("User is not authenticated");
        }

        UploadedTextExtractor.ExtractedText extractedText = UploadedTextExtractor.extract(file, MAX_FILE_SIZE, "document");
        List<String> chunks = splitIntoChunks(extractedText.content());

        KnowledgeDocument document = new KnowledgeDocument();
        document.setOriginalFilename(extractedText.filename());
        document.setContentType(resolveContentType(file, extractedText.fileType()));
        document.setFileSize(file.getSize());
        document.setCategory(category);
        document.setUploadedBy(currentUserId);
        document.setStatus("ACTIVE");

        // Save document metadata first so chunks can reference the generated document id.
        knowledgeDocumentMapper.insert(document);

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            documentChunkMapper.insert(chunk);
        }

        return convertToKnowledgeDocumentVO(document);
    }

    @Override
    public List<KnowledgeDocumentVO> listDocuments() {
        return knowledgeDocumentMapper.findActiveDocuments()
                .stream()
                .map(this::convertToKnowledgeDocumentVO)
                .toList();
    }

    @Override
    public List<DocumentChunkVO> listChunks(Long documentId) {
        KnowledgeDocument document = knowledgeDocumentMapper.findById(documentId);
        if (document == null || !"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Document not found");
        }

        return documentChunkMapper.findByDocumentId(documentId)
                .stream()
                .map(this::convertToDocumentChunkVO)
                .toList();
    }
    @Override
    public List<DocumentSearchResultVO> searchChunks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("Search keyword cannot be blank");
        }

        // Search document chunks first by keyword before adding semantic retrieval later.
        return documentChunkMapper.searchChunks(keyword.trim());
    }
    @Override
    public void archive(Long documentId) {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can archive documents");
        }

        KnowledgeDocument document = knowledgeDocumentMapper.findById(documentId);
        if (document == null || !"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Document not found");
        }

        // Archive outdated documents so they are no longer used as AI answer sources.
        knowledgeDocumentMapper.archive(documentId);
    }
    @Override
    public List<DocumentCategoryStatsVO> getCategoryStats() {
        String role = CurrentUserUtil.getCurrentUserRole();
        if (!"ADMIN".equals(role)) {
            throw new BusinessException("Only administrators can access document category stats");
        }

        // Document category stats help administrators understand how uploaded knowledge is organized.
        return knowledgeDocumentMapper.countActiveDocumentsByCategory();
    }
    private List<String> splitIntoChunks(String content) {
        String normalizedContent = content == null ? "" : content.trim();
        if (normalizedContent.isBlank()) {
            throw new BusinessException("Document content cannot be blank");
        }

        List<String> chunks = new ArrayList<>();
        String[] paragraphs = normalizedContent.split("\\R\\s*\\R");

        StringBuilder currentChunk = new StringBuilder();
        for (String paragraph : paragraphs) {
            String cleanedParagraph = paragraph.trim();
            if (cleanedParagraph.isBlank()) {
                continue;
            }

            if (currentChunk.length() + cleanedParagraph.length() > MAX_CHUNK_LENGTH) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk.setLength(0);
                }
            }

            if (cleanedParagraph.length() > MAX_CHUNK_LENGTH) {
                chunks.add(cleanedParagraph.substring(0, MAX_CHUNK_LENGTH));
                String remaining = cleanedParagraph.substring(MAX_CHUNK_LENGTH).trim();
                if (!remaining.isBlank()) {
                    currentChunk.append(remaining).append("\n\n");
                }
            } else {
                currentChunk.append(cleanedParagraph).append("\n\n");
            }
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private String resolveContentType(MultipartFile file, String fileType) {
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            return file.getContentType();
        }
        return fileType;
    }

    private KnowledgeDocumentVO convertToKnowledgeDocumentVO(KnowledgeDocument document) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setOriginalFilename(document.getOriginalFilename());
        vo.setContentType(document.getContentType());
        vo.setFileSize(document.getFileSize());
        vo.setCategory(document.getCategory());
        vo.setUploadedBy(document.getUploadedBy());
        vo.setStatus(document.getStatus());
        vo.setCreatedTime(document.getCreatedTime());
        vo.setUpdatedTime(document.getUpdatedTime());
        return vo;
    }

    private DocumentChunkVO convertToDocumentChunkVO(DocumentChunk chunk) {
        DocumentChunkVO vo = new DocumentChunkVO();
        vo.setId(chunk.getId());
        vo.setDocumentId(chunk.getDocumentId());
        vo.setChunkIndex(chunk.getChunkIndex());
        vo.setContent(chunk.getContent());
        vo.setCreatedTime(chunk.getCreatedTime());
        return vo;
    }
}
