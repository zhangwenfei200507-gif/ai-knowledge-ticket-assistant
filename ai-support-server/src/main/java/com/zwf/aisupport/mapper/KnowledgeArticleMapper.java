package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.KnowledgeArticle;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeArticleMapper {

    @Insert("""
            insert into knowledge_articles (title, content, category, created_by, status)
            values (#{title}, #{content}, #{category}, #{createdBy}, #{status})
            """)
    void insert(KnowledgeArticle article);

    @Select("""
            select * from knowledge_articles
            where status = 'PUBLISHED'
            order by updated_time desc
            """)
    List<KnowledgeArticle> findPublishedArticles();

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
}