package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.DocumentChunk;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
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
}