package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunkAdminVO {

    private Long id;
    private Long documentId;
    private String originalFilename;
    private String category;
    private Integer chunkIndex;
    private String content;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
