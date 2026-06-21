package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
public class KnowledgeMatchingStage {

    private final KnowledgeArticleMapper knowledgeArticleMapper;

    public KnowledgeMatchingStage(KnowledgeArticleMapper knowledgeArticleMapper) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
    }

    public KnowledgeMatch match(KnowledgeClassification classification, String content) {
        return knowledgeArticleMapper.findPublishedArticles().stream()
                .filter(article -> sameDomain(article.getCategory(), classification.domain()))
                .map(article -> new ArticleScore(article, score(article, classification.concepts())))
                .filter(score -> score.score() >= 6)
                .max(Comparator.comparingInt(ArticleScore::score))
                .map(score -> KnowledgeMatch.reorganize(score.article().getId()))
                .orElseGet(KnowledgeMatch::createNewPack);
    }

    private int score(KnowledgeArticle article, List<String> concepts) {
        String articleText = TextUtil.normalizeSearchText(article.getTitle() + " " + article.getCategory() + " " + article.getContent());
        int score = 0;
        for (String concept : concepts) {
            if (articleText.contains(concept)) {
                score += concept.length() >= 8 ? 2 : 1;
            }
        }
        return score;
    }

    private boolean sameDomain(String articleCategory, String incomingDomain) {
        return normalizeDomain(articleCategory).equals(normalizeDomain(incomingDomain));
    }

    private String normalizeDomain(String category) {
        return category == null || category.isBlank()
                ? "general"
                : category.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private record ArticleScore(KnowledgeArticle article, int score) {
    }
}
