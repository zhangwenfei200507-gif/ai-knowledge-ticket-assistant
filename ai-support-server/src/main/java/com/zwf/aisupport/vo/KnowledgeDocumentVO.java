package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentVO {

    private Long id;
    private String originalFilename;
    private String contentType;
    private Long fileSize;
    private String category;
    private Long uploadedBy;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}