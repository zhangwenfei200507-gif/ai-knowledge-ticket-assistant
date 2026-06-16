package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.vo.DocumentSearchResultVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentChunkMapper {

    @Insert("""
            insert into document_chunks (document_id, chunk_index, content)
            values (#{documentId}, #{chunkIndex}, #{content})
            """)
    void insert(DocumentChunk chunk);

    @Select("""
            select * from document_chunks
            where document_id = #{documentId}
            order by chunk_index asc
            """)
    List<DocumentChunk> findByDocumentId(Long documentId);

    @Select("""
        select dc.id as chunk_id,
               dc.document_id,
               d.original_filename,
               d.category,
               dc.chunk_index,
               dc.content
        from document_chunks dc
        join documents d on d.id = dc.document_id
        where d.status = 'ACTIVE'
          and lower(dc.content) like lower(concat('%', #{keyword}, '%'))
        order by d.created_time desc, dc.chunk_index asc
        """)
    List<DocumentSearchResultVO> searchChunks(@Param("keyword") String keyword);
}