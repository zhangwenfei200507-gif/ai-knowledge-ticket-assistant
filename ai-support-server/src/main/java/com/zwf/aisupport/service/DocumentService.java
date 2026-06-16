package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.DocumentChunkVO;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import com.zwf.aisupport.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    KnowledgeDocumentVO upload(MultipartFile file, String category);

    List<KnowledgeDocumentVO> listDocuments();

    List<DocumentChunkVO> listChunks(Long documentId);

    List<DocumentSearchResultVO> searchChunks(String keyword);

    void archive(Long documentId);
}