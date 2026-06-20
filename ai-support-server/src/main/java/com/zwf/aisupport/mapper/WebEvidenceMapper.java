package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.WebEvidence;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface WebEvidenceMapper {

    @Insert("""
        insert into web_evidence (
            question,
            query,
            title,
            url,
            domain,
            snippet,
            extracted_content,
            suggested_article_title,
            suggested_article_content,
            category,
            credibility_score,
            evidence_origin,
            status,
            created_by
        )
        values (
            #{question},
            #{query},
            #{title},
            #{url},
            #{domain},
            #{snippet},
            #{extractedContent},
            #{suggestedArticleTitle},
            #{suggestedArticleContent},
            #{category},
            #{credibilityScore},
            #{evidenceOrigin},
            #{status},
            #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(WebEvidence evidence);

    @Select("""
        select * from web_evidence
        order by created_time desc
        """)
    List<WebEvidence> findAll();

    @Select("""
        select * from web_evidence
        where status = 'NEEDS_REVIEW'
        order by created_time desc
        """)
    List<WebEvidence> findPendingReview();

    @Select("select * from web_evidence where id = #{id}")
    WebEvidence findById(Long id);

    @Update("""
        update web_evidence
        set status = 'CONVERTED',
            reviewed_by = #{reviewedBy},
            converted_article_id = #{convertedArticleId},
            reviewed_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void markConverted(@Param("id") Long id,
                       @Param("reviewedBy") Long reviewedBy,
                       @Param("convertedArticleId") Long convertedArticleId);

    @Update("""
        update web_evidence
        set status = 'REJECTED',
            reviewed_by = #{reviewedBy},
            reviewed_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void markRejected(@Param("id") Long id,
                      @Param("reviewedBy") Long reviewedBy);

    @Update("""
        update web_evidence
        set suggested_article_title = #{suggestedArticleTitle},
            suggested_article_content = #{suggestedArticleContent}
        where id = #{id}
        """)
    void updateSuggestedArticle(@Param("id") Long id,
                                @Param("suggestedArticleTitle") String suggestedArticleTitle,
                                @Param("suggestedArticleContent") String suggestedArticleContent);

    @Select("select count(*) from web_evidence")
    Long countAll();

    @Select("select count(*) from web_evidence where status = 'NEEDS_REVIEW'")
    Long countPendingReview();

    @Select("select count(*) from web_evidence where status in ('NEEDS_REVIEW', 'CONVERTED')")
    Long countUsefulEvidence();

    @Select("select count(*) from web_evidence where status = 'CONVERTED'")
    Long countConvertedToArticles();

    @Select("select count(distinct domain) from web_evidence where domain is not null and domain <> ''")
    Long countDistinctDomains();
}
