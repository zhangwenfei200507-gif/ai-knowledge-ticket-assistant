package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper {

    @Insert("""
            insert into document_chunks (document_id, chunk_index, content)
            values (#{documentId}, #{chunkIndex}, #{content})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(DocumentChunk chunk);

    @Select("""
            select * from document_chunks
            where document_id = #{documentId}
            order by chunk_index asc
            """)
    List<DocumentChunk> findByDocumentId(Long documentId);

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
          and (
              to_tsvector('english', dc.content) @@ search_query.query
              or lower(dc.content) like lower(concat('%', #{keyword}, '%'))
          )
        order by relevance_score desc, d.created_time desc, dc.chunk_index asc
        limit 5
        """)
    List<DocumentSearchResultVO> searchChunks(@Param("keyword") String keyword);
}
