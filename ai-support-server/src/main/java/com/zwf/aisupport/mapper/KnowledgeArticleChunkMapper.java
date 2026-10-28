package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.KnowledgeArticleChunk;
import com.zwf.aisupport.vo.KnowledgeArticleChunkSearchResultVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeArticleChunkMapper {

    @Insert("""
            insert into knowledge_article_chunks (
                article_id,
                chunk_index,
                section_title,
                section_type,
                concept_tags,
                content,
                status
            )
            values (
                #{articleId},
                #{chunkIndex},
                #{sectionTitle},
                #{sectionType},
                #{conceptTags},
                #{content},
                'ACTIVE'
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeArticleChunk chunk);

    @Select("""
            select id,
                   article_id as article_id,
                   chunk_index as chunk_index,
                   section_title as section_title,
                   section_type as section_type,
                   concept_tags as concept_tags,
                   content,
                   status,
                   created_time as created_time,
                   updated_time as updated_time
            from knowledge_article_chunks
            where article_id = #{articleId}
              and status = 'ACTIVE'
            order by chunk_index asc
            """)
    List<KnowledgeArticleChunk> findActiveByArticleId(Long articleId);

    @Select("""
            with search_query as (
                select websearch_to_tsquery('english', #{keyword}) as query
            )
            select kac.id as chunk_id,
                   kac.article_id,
                   ka.title as article_title,
                   ka.category,
                   kac.chunk_index,
                   kac.section_title,
                   kac.section_type,
                   kac.concept_tags,
                   kac.content,
                   (
                       ts_rank(
                           to_tsvector('english', kac.content || ' ' || coalesce(kac.concept_tags, '') || ' ' || kac.section_title),
                           search_query.query
                       )
                       + case when cast(#{category} as varchar) is not null
                               and lower(coalesce(ka.category, '')) = lower(cast(#{category} as varchar))
                              then 0.30 else 0 end
                   ) as relevance_score
            from knowledge_article_chunks kac
            join knowledge_articles ka on ka.id = kac.article_id
            cross join search_query
            where ka.status = 'PUBLISHED'
              and kac.status = 'ACTIVE'
              and (cast(#{category} as varchar) is null or lower(coalesce(ka.category, '')) = lower(cast(#{category} as varchar)))
              and (
                  to_tsvector('english', kac.content || ' ' || coalesce(kac.concept_tags, '') || ' ' || kac.section_title) @@ search_query.query
                  or lower(kac.content) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(coalesce(kac.concept_tags, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(kac.section_title) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              )
            order by relevance_score desc, kac.article_id asc, kac.chunk_index asc
            limit #{limit}
            """)
    List<KnowledgeArticleChunkSearchResultVO> searchChunks(@Param("keyword") String keyword,
                                                           @Param("category") String category,
                                                           @Param("limit") int limit);

    @Select("""
            select count(*)
            from knowledge_article_chunks kac
            join knowledge_articles ka on ka.id = kac.article_id
            where ka.status = 'PUBLISHED'
              and kac.status = 'ACTIVE'
            """)
    long countActivePublishedChunks();

    @Update("""
            update knowledge_article_chunks
            set status = 'ARCHIVED',
                updated_time = current_timestamp
            where article_id = #{articleId}
              and status = 'ACTIVE'
            """)
    int archiveActiveByArticleId(Long articleId);
}
