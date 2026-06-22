package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.vo.KnowledgeCategoryStatsVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface KnowledgeArticleMapper {

    @Insert("""
        insert into knowledge_articles (title, content, category, created_by, status)
        values (#{title}, #{content}, #{category}, #{createdBy}, #{status})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(KnowledgeArticle article);

    @Select("""
            select * from knowledge_articles
            where status = 'PUBLISHED'
            order by updated_time desc
            """)
    List<KnowledgeArticle> findPublishedArticles();

    @Select("""
        select * from knowledge_articles
        where (cast(#{status} as varchar) is null or status = #{status})
          and (cast(#{category} as varchar) is null or lower(coalesce(category, '')) = lower(#{category}))
          and (
              cast(#{keyword} as varchar) is null
              or lower(title) like lower(concat('%', #{keyword}, '%'))
              or lower(content) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(category, '')) like lower(concat('%', #{keyword}, '%'))
          )
        order by updated_time desc
        limit #{size} offset #{offset}
        """)
    List<KnowledgeArticle> findPage(@Param("keyword") String keyword,
                                    @Param("category") String category,
                                    @Param("status") String status,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    @Select("""
        select count(*) from knowledge_articles
        where (cast(#{status} as varchar) is null or status = #{status})
          and (cast(#{category} as varchar) is null or lower(coalesce(category, '')) = lower(#{category}))
          and (
              cast(#{keyword} as varchar) is null
              or lower(title) like lower(concat('%', #{keyword}, '%'))
              or lower(content) like lower(concat('%', #{keyword}, '%'))
              or lower(coalesce(category, '')) like lower(concat('%', #{keyword}, '%'))
          )
        """)
    Long countPage(@Param("keyword") String keyword,
                   @Param("category") String category,
                   @Param("status") String status);

    @Select("select * from knowledge_articles where id = #{id}")
    KnowledgeArticle findById(Long id);

    @Select("""
        select * from knowledge_articles
        where status = 'PUBLISHED'
          and (
              lower(title) like lower(concat('%', #{keyword}, '%'))
              or lower(content) like lower(concat('%', #{keyword}, '%'))
          )
        order by updated_time desc
        """)
    List<KnowledgeArticle> searchPublishedArticles(@Param("keyword") String keyword);

    @Update("""
        update knowledge_articles
        set title = #{title},
            content = #{content},
            category = #{category},
            updated_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void update(KnowledgeArticle article);

    @Update("""
        update knowledge_articles
        set status = 'ARCHIVED',
            updated_time = CURRENT_TIMESTAMP
        where id = #{id}
        """)
    void archive(Long id);

    @Select("""
        select coalesce(category, 'Uncategorized') as category,
               count(*) as article_count
        from knowledge_articles
        where status = 'PUBLISHED'
        group by coalesce(category, 'Uncategorized')
        order by article_count desc
        """)
    List<KnowledgeCategoryStatsVO> countPublishedArticlesByCategory();

    @Select("select count(*) from knowledge_articles where status = 'PUBLISHED'")
    Long countPublishedArticles();
}
