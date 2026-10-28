package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class KnowledgeArticleChunkSearchResultVO {

    private Long chunkId;
    private Long articleId;
    private String articleTitle;
    private String category;
    private Integer chunkIndex;
    private String sectionTitle;
    private String sectionType;
    private String conceptTags;
    private String content;
    private Double relevanceScore;
}
