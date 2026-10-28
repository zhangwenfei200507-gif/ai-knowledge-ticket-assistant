package com.zwf.aisupport.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeArticleChunk {

    private Long id;
    private Long articleId;
    private Integer chunkIndex;
    private String sectionTitle;
    private String sectionType;
    private String conceptTags;
    private String content;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
