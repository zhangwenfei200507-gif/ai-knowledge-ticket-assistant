package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.DocumentChunkUpdateDTO;
import com.zwf.aisupport.vo.DocumentCategoryStatsVO;
import com.zwf.aisupport.vo.DocumentChunkAdminVO;
import com.zwf.aisupport.vo.DocumentChunkVO;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import com.zwf.aisupport.vo.KnowledgeDocumentVO;
import com.zwf.aisupport.vo.PageVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    KnowledgeDocumentVO upload(MultipartFile file, String category);

    PageVO<KnowledgeDocumentVO> listDocuments(String keyword, String category, String status, Integer page, Integer size);

    KnowledgeDocumentVO getDocument(Long documentId);

    List<DocumentChunkVO> listChunks(Long documentId);

    PageVO<DocumentChunkAdminVO> listChunksForAdmin(String keyword,
                                                    Long documentId,
                                                    String category,
                                                    String status,
                                                    Integer page,
                                                    Integer size);

    DocumentChunkAdminVO updateChunk(Long chunkId, DocumentChunkUpdateDTO updateDTO);

    void archiveChunk(Long chunkId);

    List<DocumentSearchResultVO> searchChunks(String keyword);

    void archive(Long documentId);

    List<DocumentCategoryStatsVO> getCategoryStats();
}
