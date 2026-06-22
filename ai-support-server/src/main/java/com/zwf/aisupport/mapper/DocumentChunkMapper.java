package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.vo.DocumentChunkAdminVO;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DocumentChunkMapper {

    @Insert("""
            insert into document_chunks (document_id, chunk_index, content, status)
            values (#{documentId}, #{chunkIndex}, #{content}, 'ACTIVE')
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DocumentChunk chunk);

    @Select("""
            select * from document_chunks
            where document_id = #{documentId}
              and status = 'ACTIVE'
            order by chunk_index asc
            """)
    List<DocumentChunk> findByDocumentId(Long documentId);

    @Select("""
            select * from document_chunks
            where id = #{id}
            """)
    DocumentChunk findById(Long id);

    @Select("""
            select dc.id,
                   dc.document_id,
                   d.original_filename,
                   d.category,
                   dc.chunk_index,
                   dc.content,
                   dc.status,
                   dc.created_time,
                   dc.updated_time
            from document_chunks dc
            join documents d on d.id = dc.document_id
            where d.status = 'ACTIVE'
              and (cast(#{status} as varchar) is null or dc.status = #{status})
              and (cast(#{documentId} as bigint) is null or dc.document_id = #{documentId})
              and (cast(#{category} as varchar) is null or d.category = #{category})
              and (
                  cast(#{keyword} as varchar) is null
                  or lower(dc.content) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(d.original_filename) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(coalesce(d.category, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              )
            order by dc.updated_time desc, dc.created_time desc, dc.id desc
            limit #{size} offset #{offset}
            """)
    List<DocumentChunkAdminVO> findAdminPage(@Param("keyword") String keyword,
                                             @Param("documentId") Long documentId,
                                             @Param("category") String category,
                                             @Param("status") String status,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    @Select("""
            select count(*)
            from document_chunks dc
            join documents d on d.id = dc.document_id
            where d.status = 'ACTIVE'
              and (cast(#{status} as varchar) is null or dc.status = #{status})
              and (cast(#{documentId} as bigint) is null or dc.document_id = #{documentId})
              and (cast(#{category} as varchar) is null or d.category = #{category})
              and (
                  cast(#{keyword} as varchar) is null
                  or lower(dc.content) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(d.original_filename) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(coalesce(d.category, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              )
            """)
    long countAdminPage(@Param("keyword") String keyword,
                        @Param("documentId") Long documentId,
                        @Param("category") String category,
                        @Param("status") String status);

    @Select("""
            select dc.id,
                   dc.document_id,
                   d.original_filename,
                   d.category,
                   dc.chunk_index,
                   dc.content,
                   dc.status,
                   dc.created_time,
                   dc.updated_time
            from document_chunks dc
            join documents d on d.id = dc.document_id
            where dc.id = #{id}
            """)
    DocumentChunkAdminVO findAdminById(Long id);

    @Select("""
        with search_query as (
            select websearch_to_tsquery('english', #{keyword}) as query
        )
        select dc.id as chunk_id,
               dc.document_id,
               d.original_filename,
               d.category,
               dc.chunk_index,
               dc.content,
               ts_rank(to_tsvector('english', dc.content), search_query.query) as relevance_score
        from document_chunks dc
        join documents d on d.id = dc.document_id
        cross join search_query
        where d.status = 'ACTIVE'
          and dc.status = 'ACTIVE'
          and (
              to_tsvector('english', dc.content) @@ search_query.query
              or lower(dc.content) like lower(concat('%', cast(#{keyword} as varchar), '%'))
          )
        order by relevance_score desc, d.created_time desc, dc.chunk_index asc
        limit 5
        """)
    List<DocumentSearchResultVO> searchChunks(@Param("keyword") String keyword);

    @Select("""
        with search_query as (
            select websearch_to_tsquery('english', #{keyword}) as query
        )
        select dc.id as chunk_id,
               dc.document_id,
               d.original_filename,
               d.category,
               dc.chunk_index,
               dc.content,
               ts_rank(to_tsvector('english', dc.content), search_query.query) as relevance_score
        from document_chunks dc
        join documents d on d.id = dc.document_id
        cross join search_query
        where d.status = 'ACTIVE'
          and dc.status = 'ACTIVE'
          and dc.document_id = #{documentId}
          and (
              to_tsvector('english', dc.content) @@ search_query.query
              or lower(dc.content) like lower(concat('%', cast(#{keyword} as varchar), '%'))
          )
        order by relevance_score desc, dc.chunk_index asc
        limit 5
        """)
    List<DocumentSearchResultVO> searchChunksByDocumentId(@Param("documentId") Long documentId,
                                                          @Param("keyword") String keyword);

    @Update("""
            update document_chunks
            set content = #{content},
                updated_time = current_timestamp
            where id = #{id}
              and status = 'ACTIVE'
            """)
    int updateContent(@Param("id") Long id, @Param("content") String content);

    @Update("""
            update document_chunks
            set status = 'ARCHIVED',
                updated_time = current_timestamp
            where id = #{id}
              and status = 'ACTIVE'
            """)
    int archive(Long id);

    @Update("""
            update document_chunks
            set status = 'ARCHIVED',
                updated_time = current_timestamp
            where document_id = #{documentId}
              and status = 'ACTIVE'
            """)
    int archiveActiveByDocumentId(Long documentId);
}
