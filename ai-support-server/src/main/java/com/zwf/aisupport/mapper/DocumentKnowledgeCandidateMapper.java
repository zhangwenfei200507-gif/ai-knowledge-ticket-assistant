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
            #{status},
            #{createdBy}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DocumentKnowledgeCandidate candidate);

    @Select("""
        select dkc.*,
               d.original_filename
        from document_knowledge_candidates dkc
        join documents d on d.id = dkc.document_id
        order by dkc.created_time desc
        """)
    List<DocumentKnowledgeCandidateVO> findAll();

    @Select("""
        select dkc.*,
               d.original_filename
        from document_knowledge_candidates dkc
        join documents d on d.id = dkc.document_id
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

    @Select("select count(*) from document_knowledge_candidates where status = 'NEEDS_REVIEW'")
    Long countPendingReview();
}
