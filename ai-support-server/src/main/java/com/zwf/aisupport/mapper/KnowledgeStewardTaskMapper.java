package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.KnowledgeStewardTask;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeStewardTaskMapper {

    @Insert("""
        insert into knowledge_steward_tasks (
            source_type,
            source_id,
            source_origin,
            source_title,
            source_question,
            source_excerpt,
            recommended_action,
            target_domain,
            target_article_id,
            target_article_title,
            target_section,
            domain_confidence,
            coverage_confidence,
            source_quality,
            duplicate_risk,
            proposal_title,
            proposal_content,
            reasoning,
            warnings,
            status,
            created_by
        )
        values (
            #{sourceType},
            #{sourceId},
            #{sourceOrigin},
            #{sourceTitle},
            #{sourceQuestion},
            #{sourceExcerpt},
            #{recommendedAction},
            #{targetDomain},
            #{targetArticleId},
            #{targetArticleTitle},
            #{targetSection},
            #{domainConfidence},
            #{coverageConfidence},
            #{sourceQuality},
            #{duplicateRisk},
            #{proposalTitle},
            #{proposalContent},
            #{reasoning},
            #{warnings},
            #{status},
            #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeStewardTask task);

    @Select("""
        select count(*)
        from knowledge_steward_tasks
        where source_type = #{sourceType}
          and source_id = #{sourceId}
          and status in ('NEEDS_ADMIN_REVIEW', 'APPROVED')
        """)
    long countActiveBySource(@Param("sourceType") String sourceType,
                             @Param("sourceId") Long sourceId);

    @Select("""
        select * from knowledge_steward_tasks
        where status <> 'ARCHIVED'
          and (cast(#{status} as varchar) is null or status = #{status})
          and (cast(#{sourceType} as varchar) is null or source_type = #{sourceType})
          and (cast(#{targetDomain} as varchar) is null or lower(target_domain) = lower(cast(#{targetDomain} as varchar)))
          and (
              cast(#{keyword} as varchar) is null
              or lower(coalesce(source_title, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(coalesce(source_question, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(proposal_title) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(proposal_content) like lower(concat('%', cast(#{keyword} as varchar), '%'))
          )
        order by created_time desc
        limit #{size} offset #{offset}
        """)
    List<KnowledgeStewardTask> findPage(@Param("keyword") String keyword,
                                        @Param("status") String status,
                                        @Param("sourceType") String sourceType,
                                        @Param("targetDomain") String targetDomain,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    @Select("""
        select count(*)
        from knowledge_steward_tasks
        where status <> 'ARCHIVED'
          and (cast(#{status} as varchar) is null or status = #{status})
          and (cast(#{sourceType} as varchar) is null or source_type = #{sourceType})
          and (cast(#{targetDomain} as varchar) is null or lower(target_domain) = lower(cast(#{targetDomain} as varchar)))
          and (
              cast(#{keyword} as varchar) is null
              or lower(coalesce(source_title, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(coalesce(source_question, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(proposal_title) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(proposal_content) like lower(concat('%', cast(#{keyword} as varchar), '%'))
          )
        """)
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("sourceType") String sourceType,
                   @Param("targetDomain") String targetDomain);

    @Select("select * from knowledge_steward_tasks where id = #{id}")
    KnowledgeStewardTask findById(Long id);

    @Update("""
        update knowledge_steward_tasks
        set status = 'APPROVED',
            reviewed_by = #{reviewedBy},
            converted_article_id = #{convertedArticleId},
            reviewed_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void markApproved(@Param("id") Long id,
                      @Param("reviewedBy") Long reviewedBy,
                      @Param("convertedArticleId") Long convertedArticleId);

    @Update("""
        update knowledge_steward_tasks
        set status = 'REJECTED',
            reviewed_by = #{reviewedBy},
            reviewed_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void markRejected(@Param("id") Long id,
                      @Param("reviewedBy") Long reviewedBy);

    @Update("""
        update knowledge_steward_tasks
        set status = 'ARCHIVED',
            reviewed_by = #{reviewedBy},
            reviewed_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void markArchived(@Param("id") Long id,
                      @Param("reviewedBy") Long reviewedBy);

    @Update("""
        update knowledge_steward_tasks
        set proposal_title = #{proposalTitle},
            proposal_content = #{proposalContent},
            target_domain = #{targetDomain},
            target_article_id = #{targetArticleId},
            target_article_title = #{targetArticleTitle},
            target_section = #{targetSection},
            recommended_action = #{recommendedAction},
            warnings = #{warnings}
        where id = #{id}
        """)
    void updateProposal(KnowledgeStewardTask task);
}
