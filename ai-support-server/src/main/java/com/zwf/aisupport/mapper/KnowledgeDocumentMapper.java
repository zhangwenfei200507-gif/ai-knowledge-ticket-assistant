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
        select d.* from documents d
        where (cast(#{status} as varchar) is null or d.status = #{status})
          and (cast(#{category} as varchar) is null or lower(coalesce(d.category, '')) = lower(cast(#{category} as varchar)))
          and (
              #{admin} = true
              or d.uploaded_by = #{userId}
              or exists (
                  select 1 from users uploader
                  where uploader.id = d.uploaded_by
                    and uploader.role = 'ADMIN'
                    and uploader.status = 1
              )
          )
          and (
              cast(#{keyword} as varchar) is null
              or lower(d.original_filename) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(coalesce(d.category, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(coalesce(d.content_type, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
          )
        order by d.created_time desc
        limit #{size} offset #{offset}
        """)
    List<KnowledgeDocument> findPage(@Param("keyword") String keyword,
                                     @Param("category") String category,
                                     @Param("status") String status,
                                     @Param("userId") Long userId,
                                     @Param("admin") boolean admin,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    @Select("""
        select count(*) from documents d
        where (cast(#{status} as varchar) is null or d.status = #{status})
          and (cast(#{category} as varchar) is null or lower(coalesce(d.category, '')) = lower(cast(#{category} as varchar)))
          and (
              #{admin} = true
              or d.uploaded_by = #{userId}
              or exists (
                  select 1 from users uploader
                  where uploader.id = d.uploaded_by
                    and uploader.role = 'ADMIN'
                    and uploader.status = 1
              )
          )
          and (
              cast(#{keyword} as varchar) is null
              or lower(d.original_filename) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(coalesce(d.category, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              or lower(coalesce(d.content_type, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
          )
        """)
    Long countPage(@Param("keyword") String keyword,
                   @Param("category") String category,
                   @Param("status") String status,
                   @Param("userId") Long userId,
                   @Param("admin") boolean admin);

    @Select("select * from documents where id = #{id}")
    KnowledgeDocument findById(Long id);

    @Select("""
        select d.* from documents d
        where d.id = #{id}
          and (
              #{admin} = true
              or d.uploaded_by = #{userId}
              or exists (
                  select 1 from users uploader
                  where uploader.id = d.uploaded_by
                    and uploader.role = 'ADMIN'
                    and uploader.status = 1
              )
          )
        """)
    KnowledgeDocument findAccessibleById(@Param("id") Long id,
                                         @Param("userId") Long userId,
                                         @Param("admin") boolean admin);

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
