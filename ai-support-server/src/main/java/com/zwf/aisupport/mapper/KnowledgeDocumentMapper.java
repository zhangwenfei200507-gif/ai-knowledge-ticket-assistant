package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper {

    @Insert("""
            insert into documents (
                original_filename, content_type, file_size, category, uploaded_by, status
            )
            values (
                #{originalFilename}, #{contentType}, #{fileSize}, #{category}, #{uploadedBy}, #{status}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeDocument document);

    @Select("""
            select * from documents
            where status = 'ACTIVE'
            order by created_time desc
            """)
    List<KnowledgeDocument> findActiveDocuments();

    @Select("select * from documents where id = #{id}")
    KnowledgeDocument findById(Long id);
}