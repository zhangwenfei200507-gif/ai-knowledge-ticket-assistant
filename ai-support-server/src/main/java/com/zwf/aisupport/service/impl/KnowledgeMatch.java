package com.zwf.aisupport.service.impl;

public record KnowledgeMatch(Long articleId, String action) {

    public static KnowledgeMatch createNewPack() {
        return new KnowledgeMatch(null, "CREATE_KNOWLEDGE_PACK");
    }

    public static KnowledgeMatch reorganize(Long articleId) {
        return new KnowledgeMatch(articleId, "REORGANIZE_KNOWLEDGE");
    }
}
