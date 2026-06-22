package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.common.util.PageRequestUtil;
import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.common.util.UploadedTextExtractor;
import com.zwf.aisupport.dto.DocumentChunkUpdateDTO;
import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.entity.DocumentKnowledgeCandidate;
import com.zwf.aisupport.entity.KnowledgeDocument;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.DocumentKnowledgeCandidateMapper;
import com.zwf.aisupport.mapper.KnowledgeDocumentMapper;
import com.zwf.aisupport.service.DocumentService;
import com.zwf.aisupport.vo.DocumentCategoryStatsVO;
import com.zwf.aisupport.vo.DocumentChunkAdminVO;
import com.zwf.aisupport.vo.DocumentChunkVO;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import com.zwf.aisupport.vo.KnowledgeDocumentVO;
import com.zwf.aisupport.vo.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_LEARNING_CANDIDATES_PER_DOCUMENT = 3;

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentKnowledgeCandidateMapper candidateMapper;
    private final KnowledgeExtractionStage extractionStage;
    private final KnowledgeClassificationStage classificationStage;
    private final KnowledgeMatchingStage matchingStage;
    private final SemanticChunkManagementStage chunkManagementStage;

    public DocumentServiceImpl(KnowledgeDocumentMapper knowledgeDocumentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               DocumentKnowledgeCandidateMapper candidateMapper,
                               KnowledgeExtractionStage extractionStage,
                               KnowledgeClassificationStage classificationStage,
                               KnowledgeMatchingStage matchingStage,
                               SemanticChunkManagementStage chunkManagementStage) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.candidateMapper = candidateMapper;
        this.extractionStage = extractionStage;
        this.classificationStage = classificationStage;
        this.matchingStage = matchingStage;
        this.chunkManagementStage = chunkManagementStage;
    }

    @Override
    public KnowledgeDocumentVO upload(MultipartFile file, String category) {
        Long currentUserId = CurrentUserUtil.requireCurrentUserId();

        UploadedTextExtractor.ExtractedText extractedText = UploadedTextExtractor.extract(file, MAX_FILE_SIZE, "document");
        List<String> chunks = chunkManagementStage.splitDocument(extractedText.content());

        KnowledgeDocument document = new KnowledgeDocument();
        document.setOriginalFilename(extractedText.filename());
        document.setContentType(resolveContentType(file, extractedText.fileType()));
        document.setFileSize(file.getSize());
        document.setCategory(category);
        document.setUploadedBy(currentUserId);
        document.setStatus("ACTIVE");

        knowledgeDocumentMapper.insert(document);

        List<DocumentChunk> savedChunks = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocumentId(document.getId());
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            documentChunkMapper.insert(chunk);
            savedChunks.add(chunk);
        }
        createLearningCandidates(document, savedChunks, currentUserId);

        return convertToKnowledgeDocumentVO(document);
    }

    @Override
    public PageVO<KnowledgeDocumentVO> listDocuments(String keyword, String category, String status, Integer page, Integer size) {
        int safePage = PageRequestUtil.page(page);
        int safeSize = PageRequestUtil.size(size);
        String safeStatus = PageRequestUtil.optionalText(status);
        if (safeStatus == null) {
            safeStatus = "ACTIVE";
        }
        List<KnowledgeDocumentVO> items = knowledgeDocumentMapper.findPage(
                        PageRequestUtil.optionalText(keyword),
                        PageRequestUtil.optionalText(category),
                        safeStatus,
                        PageRequestUtil.offset(safePage, safeSize),
                        safeSize
                )
                .stream()
                .map(this::convertToKnowledgeDocumentVO)
                .toList();
        long total = knowledgeDocumentMapper.countPage(
                PageRequestUtil.optionalText(keyword),
                PageRequestUtil.optionalText(category),
                safeStatus
        );
        return PageVO.of(items, safePage, safeSize, total);
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
    public PageVO<DocumentChunkAdminVO> listChunksForAdmin(String keyword,
                                                          Long documentId,
                                                          String category,
                                                          String status,
                                                          Integer page,
                                                          Integer size) {
        CurrentUserUtil.requireAdmin("Only administrators can manage document chunks");

        int safePage = PageRequestUtil.page(page);
        int safeSize = PageRequestUtil.size(size);
        String safeStatus = normalizeChunkStatus(status);
        List<DocumentChunkAdminVO> items = documentChunkMapper.findAdminPage(
                PageRequestUtil.optionalText(keyword),
                documentId,
                PageRequestUtil.optionalText(category),
                safeStatus,
                PageRequestUtil.offset(safePage, safeSize),
                safeSize
        );
        long total = documentChunkMapper.countAdminPage(
                PageRequestUtil.optionalText(keyword),
                documentId,
                PageRequestUtil.optionalText(category),
                safeStatus
        );
        return PageVO.of(items, safePage, safeSize, total);
    }

    @Override
    public DocumentChunkAdminVO updateChunk(Long chunkId, DocumentChunkUpdateDTO updateDTO) {
        CurrentUserUtil.requireAdmin("Only administrators can update document chunks");
        requireActiveChunk(chunkId);

        String content = updateDTO.getContent().trim();
        if (documentChunkMapper.updateContent(chunkId, content) == 0) {
            throw new BusinessException("Chunk update failed");
        }

        return findAdminChunkById(chunkId);
    }

    @Override
    public void archiveChunk(Long chunkId) {
        CurrentUserUtil.requireAdmin("Only administrators can archive document chunks");
        requireActiveChunk(chunkId);

        if (documentChunkMapper.archive(chunkId) == 0) {
            throw new BusinessException("Chunk archive failed");
        }
    }

    @Override
    public List<DocumentSearchResultVO> searchChunks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException("Search keyword cannot be blank");
        }

        return documentChunkMapper.searchChunks(keyword.trim());
    }

    @Override
    public void archive(Long documentId) {
        CurrentUserUtil.requireAdmin("Only administrators can archive documents");

        KnowledgeDocument document = knowledgeDocumentMapper.findById(documentId);
        if (document == null || !"ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("Document not found");
        }

        knowledgeDocumentMapper.archive(documentId);
    }

    @Override
    public List<DocumentCategoryStatsVO> getCategoryStats() {
        CurrentUserUtil.requireAdmin("Only administrators can access document category stats");
        return knowledgeDocumentMapper.countActiveDocumentsByCategory();
    }

    private void createLearningCandidates(KnowledgeDocument document, List<DocumentChunk> chunks, Long currentUserId) {
        chunks.stream()
                .map(chunk -> new CandidateDraft(chunk, extractionStage.scoreLearningValue(
                        document.getCategory(),
                        document.getOriginalFilename(),
                        chunk.getContent()
                )))
                .filter(draft -> draft.score() >= 7)
                .sorted((first, second) -> Integer.compare(second.score(), first.score()))
                .limit(MAX_LEARNING_CANDIDATES_PER_DOCUMENT)
                .forEach(draft -> candidateMapper.insert(buildCandidate(document, draft.chunk(), draft.score(), currentUserId)));
    }

    private DocumentKnowledgeCandidate buildCandidate(KnowledgeDocument document,
                                                      DocumentChunk chunk,
                                                      int score,
                                                      Long currentUserId) {
        KnowledgeClassification classification = classificationStage.classify(
                document.getCategory(),
                document.getOriginalFilename(),
                chunk.getContent()
        );
        KnowledgeMatch match = matchingStage.match(classification, chunk.getContent());

        DocumentKnowledgeCandidate candidate = new DocumentKnowledgeCandidate();
        candidate.setDocumentId(document.getId());
        candidate.setChunkId(chunk.getId());
        candidate.setTitle(buildCandidateTitle(classification.domain(), chunk.getContent()));
        candidate.setContent(buildCandidateContent(chunk, classification));
        candidate.setCategory(classification.domain());
        candidate.setImportanceScore(new BigDecimal(score).movePointLeft(1).min(new BigDecimal("0.99")));
        candidate.setSuggestedArticleId(match.articleId());
        candidate.setSuggestedAction(match.action());
        candidate.setStatus("NEEDS_REVIEW");
        candidate.setCreatedBy(currentUserId);
        return candidate;
    }

    private String buildCandidateTitle(String category, String content) {
        String topic = extractionStage.firstUsefulSentence(content);
        if (topic.length() > 70) {
            topic = topic.substring(0, 67).trim() + "...";
        }
        return TextUtil.limit(category + " Knowledge Insight - " + topic, 180);
    }

    private String buildCandidateContent(DocumentChunk chunk, KnowledgeClassification classification) {
        List<String> points = extractionStage.extractOperationalPoints(chunk.getContent());
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(buildCandidateTitle(classification.domain(), chunk.getContent())).append("\n\n")
                .append("Category: ").append(classification.domain()).append("\n")
                .append("Type: ").append(classification.type()).append("\n")
                .append("\n## Core Knowledge\n");

        for (String point : points) {
            builder.append("- ").append(point).append("\n");
        }

        return builder.toString().trim();
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
        vo.setStatus(chunk.getStatus());
        vo.setCreatedTime(chunk.getCreatedTime());
        vo.setUpdatedTime(chunk.getUpdatedTime());
        return vo;
    }

    private DocumentChunk requireActiveChunk(Long chunkId) {
        DocumentChunk chunk = documentChunkMapper.findById(chunkId);
        if (chunk == null || !"ACTIVE".equals(chunk.getStatus())) {
            throw new BusinessException("Chunk not found");
        }
        return chunk;
    }

    private String normalizeChunkStatus(String status) {
        String safeStatus = PageRequestUtil.optionalText(status);
        if (safeStatus == null) {
            return "ACTIVE";
        }
        return safeStatus.toUpperCase();
    }

    private DocumentChunkAdminVO findAdminChunkById(Long chunkId) {
        DocumentChunkAdminVO chunk = documentChunkMapper.findAdminById(chunkId);
        if (chunk == null) {
            throw new BusinessException("Chunk not found");
        }
        return chunk;
    }

    private record CandidateDraft(DocumentChunk chunk, int score) {
    }
}
