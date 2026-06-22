package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.KnowledgeDocument;
import com.zwf.aisupport.vo.DocumentCategoryStatsVO;
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

    @Select("""
        select * from documents
        where (#{status} is null or status = #{status})
          and (#{category} is null or lower(coalesce(category, '')) = lower(#{category}))
          and (
              #{keyword} is null
              or lower(original_filename) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(category, '')) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(content_type, '')) like lower(concat('%', #{keyword}, '%'))
          )
        order by created_time desc
        limit #{size} offset #{offset}
        """)
    List<KnowledgeDocument> findPage(@Param("keyword") String keyword,
                                     @Param("category") String category,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    @Select("""
        select count(*) from documents
        where (#{status} is null or status = #{status})
          and (#{category} is null or lower(coalesce(category, '')) = lower(#{category}))
          and (
              #{keyword} is null
              or lower(original_filename) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(category, '')) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(content_type, '')) like lower(concat('%', #{keyword}, '%'))
          )
        """)
    Long countPage(@Param("keyword") String keyword,
                   @Param("category") String category,
                   @Param("status") String status);

    @Select("select * from documents where id = #{id}")
    KnowledgeDocument findById(Long id);

    @Update("""
        update documents
        set status = 'ARCHIVED',
            updated_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void archive(Long id);

    @Select("select count(*) from documents where status = 'ACTIVE'")
    Long countActiveDocuments();

    @Select("""
        select coalesce(category, 'Uncategorized') as category,
               count(*) as document_count
        from documents
        where status = 'ACTIVE'
        group by coalesce(category, 'Uncategorized')
        order by document_count desc
        """)
    List<DocumentCategoryStatsVO> countActiveDocumentsByCategory();
}
