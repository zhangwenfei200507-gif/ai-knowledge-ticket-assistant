package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.exception.BusinessException;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgePackReorganizerTest {

    @Mock
    private KnowledgeArticleMapper knowledgeArticleMapper;

    @InjectMocks
    private KnowledgePackReorganizer reorganizer;

    @Test
    void reorganizeExistingByIdRejectsDifferentDomain() {
        KnowledgeArticle article = article(10L, "Redis Cache", "Redis Cache Knowledge Pack", "Redis cache troubleshooting");
        when(knowledgeArticleMapper.findById(10L)).thenReturn(article);

        assertThatThrownBy(() -> reorganizer.reorganizeExistingById(
                10L,
                "PostgreSQL WAL Retention",
                "PostgreSQL",
                "PostgreSQL replication slots can retain WAL when restart_lsn does not advance.",
                "test"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Knowledge domain does not match the target Knowledge Pack");
    }

    @Test
    void reorganizeOrCreateOnlyMergesSameDomainAndCleansProcessContent() {
        KnowledgeArticle redis = article(1L, "Redis Cache", "Redis Cache Knowledge Pack", "Redis cache outage checks and token storage.");
        KnowledgeArticle postgres = article(2L, "PostgreSQL", "PostgreSQL Knowledge Pack", "PostgreSQL replication slot troubleshooting.");
        when(knowledgeArticleMapper.findPublishedArticles()).thenReturn(List.of(redis, postgres));
        when(knowledgeArticleMapper.findById(2L)).thenReturn(postgres);

        reorganizer.reorganizeOrCreate(
                "PostgreSQL Replication Slots",
                "PostgreSQL",
                """
                        Title: Course introduction
                        Purpose: Marketing overview
                        PostgreSQL replication slots can retain WAL when restart_lsn is behind confirmed_flush_lsn.
                        Advertisement: enroll now for a database course.
                        Check pg_replication_slots to compare restart_lsn and confirmed_flush_lsn.
                        """,
                7L,
                "test"
        );

        ArgumentCaptor<KnowledgeArticle> articleCaptor = ArgumentCaptor.forClass(KnowledgeArticle.class);
        verify(knowledgeArticleMapper).update(articleCaptor.capture());
        KnowledgeArticle updated = articleCaptor.getValue();

        assertThat(updated.getId()).isEqualTo(2L);
        assertThat(updated.getCategory()).isEqualTo("PostgreSQL");
        assertThat(updated.getContent())
                .contains("PostgreSQL replication slots can retain WAL")
                .contains("pg_replication_slots")
                .doesNotContain("Title:")
                .doesNotContain("Purpose:")
                .doesNotContain("Advertisement")
                .doesNotContain("course");
    }

    private KnowledgeArticle article(Long id, String category, String title, String content) {
        KnowledgeArticle article = new KnowledgeArticle();
        article.setId(id);
        article.setCategory(category);
        article.setTitle(title);
        article.setContent(content);
        article.setStatus("PUBLISHED");
        return article;
    }
}
