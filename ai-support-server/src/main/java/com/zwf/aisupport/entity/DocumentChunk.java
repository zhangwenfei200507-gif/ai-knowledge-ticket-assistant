package com.zwf.aisupport.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunk {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
