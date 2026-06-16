package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentChunkVO {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private LocalDateTime createdTime;
}