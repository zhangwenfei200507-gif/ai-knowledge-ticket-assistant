package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.DocumentKnowledgeCandidate;
import com.zwf.aisupport.vo.DocumentKnowledgeCandidateVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DocumentKnowledgeCandidateMapper {

    @Insert("""
        insert into document_knowledge_candidates (
            document_id,
            chunk_id,
            title,
            content,
            category,
            importance_score,
            suggested_article_id,
            suggested_action,
            status,
            created_by
        )
        values (
            #{documentId},
            #{chunkId},
            #{title},
            #{content},
            #{category},
            #{importanceScore},
            #{suggestedArticleId},
            #{suggestedAction},
            #{status},
            #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DocumentKnowledgeCandidate candidate);

    @Select("""
        select dkc.*,
               d.original_filename,
               ka.title as suggested_article_title
        from document_knowledge_candidates dkc
        join documents d on d.id = dkc.document_id
        left join knowledge_articles ka on ka.id = dkc.suggested_article_id
        where dkc.status <> 'ARCHIVED'
        order by dkc.created_time desc
        """)
    List<DocumentKnowledgeCandidateVO> findAll();

    @Select("""
        select dkc.*,
               d.original_filename,
               ka.title as suggested_article_title
        from document_knowledge_candidates dkc
        join documents d on d.id = dkc.document_id
        left join knowledge_articles ka on ka.id = dkc.suggested_article_id
        where dkc.status <> 'ARCHIVED'
          and (#{status} is null or dkc.status = #{status})
          and (#{category} is null or lower(coalesce(dkc.category, '')) = lower(#{category}))
          and (
              #{keyword} is null
              or lower(dkc.title) like lower(concat('%', #{keyword}, '%'))
              or lower(dkc.content) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(dkc.category, '')) like lower(concat('%', #{keyword}, '%'))
              or lower(d.original_filename) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(ka.title, '')) like lower(concat('%', #{keyword}, '%'))
          )
        order by dkc.created_time desc
        limit #{size} offset #{offset}
        """)
    List<DocumentKnowledgeCandidateVO> findPage(@Param("keyword") String keyword,
                                                @Param("status") String status,
                                                @Param("category") String category,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    @Select("""
        select count(*)
        from document_knowledge_candidates dkc
        join documents d on d.id = dkc.document_id
        left join knowledge_articles ka on ka.id = dkc.suggested_article_id
        where dkc.status <> 'ARCHIVED'
          and (#{status} is null or dkc.status = #{status})
          and (#{category} is null or lower(coalesce(dkc.category, '')) = lower(#{category}))
          and (
              #{keyword} is null
              or lower(dkc.title) like lower(concat('%', #{keyword}, '%'))
              or lower(dkc.content) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(dkc.category, '')) like lower(concat('%', #{keyword}, '%'))
              or lower(d.original_filename) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(ka.title, '')) like lower(concat('%', #{keyword}, '%'))
          )
        """)
    Long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("category") String category);

    @Select("""
        select dkc.*,
               d.original_filename,
               ka.title as suggested_article_title
        from document_knowledge_candidates dkc
        join documents d on d.id = dkc.document_id
        left join knowledge_articles ka on ka.id = dkc.suggested_article_id
        where dkc.id = #{id}
        """)
    DocumentKnowledgeCandidateVO findVOById(Long id);

    @Select("select * from document_knowledge_candidates where id = #{id}")
    DocumentKnowledgeCandidate findById(Long id);

    @Update("""
        update document_knowledge_candidates
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
        update document_knowledge_candidates
        set status = 'REJECTED',
            reviewed_by = #{reviewedBy},
            reviewed_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void markRejected(@Param("id") Long id,
                      @Param("reviewedBy") Long reviewedBy);

    @Update("""
        update document_knowledge_candidates
        set title = #{title},
            content = #{content},
            category = #{category}
        where id = #{id}
        """)
    void updateReviewContent(@Param("id") Long id,
                             @Param("title") String title,
                             @Param("content") String content,
                             @Param("category") String category);

    @Update("""
        update document_knowledge_candidates
        set status = 'ARCHIVED',
            reviewed_by = #{reviewedBy},
            reviewed_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void markArchived(@Param("id") Long id,
                      @Param("reviewedBy") Long reviewedBy);

    @Delete("delete from document_knowledge_candidates where id = #{id}")
    void deleteById(Long id);

    @Select("select count(*) from document_knowledge_candidates where status = 'NEEDS_REVIEW'")
    Long countPendingReview();
}
