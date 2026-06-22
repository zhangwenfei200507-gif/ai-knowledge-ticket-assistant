package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.AiQuestionLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiQuestionLogMapper {

    @Insert("""
        insert into ai_question_logs (
            user_id,
            question,
            answer,
            source_type,
            source_article_id,
            source_title,
            source_document_id,
            source_document_name,
            source_chunk_id,
            source_chunk_index,
            suggest_create_ticket,
            created_ticket_id
        )
        values (
            #{userId},
            #{question},
            #{answer},
            #{sourceType},
            #{sourceArticleId},
            #{sourceTitle},
            #{sourceDocumentId},
            #{sourceDocumentName},
            #{sourceChunkId},
            #{sourceChunkIndex},
            #{suggestCreateTicket},
            #{createdTicketId}
        )
        """)
    void insert(AiQuestionLog aiQuestionLog);
    @Select("""
            select * from ai_question_logs
            where user_id = #{userId}
            order by created_time desc
            """)
    List<AiQuestionLog> findByUserId(Long userId);

    @Select("""
            select * from ai_question_logs
            order by created_time desc
            """)
    List<AiQuestionLog> findAll();

    @Select("""
            select * from ai_question_logs
            where (#{sourceType} is null or source_type = #{sourceType})
              and (#{feedback} is null or feedback = #{feedback})
              and (
                  #{keyword} is null
                  or lower(question) like lower(concat('%', #{keyword}, '%'))
                  or lower(answer) like lower(concat('%', #{keyword}, '%'))
                  or lower(coalesce(source_title, '')) like lower(concat('%', #{keyword}, '%'))
              )
            order by created_time desc
            limit #{size} offset #{offset}
            """)
    List<AiQuestionLog> findPage(@Param("keyword") String keyword,
                                 @Param("sourceType") String sourceType,
                                 @Param("feedback") String feedback,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    @Select("""
            select count(*) from ai_question_logs
            where (#{sourceType} is null or source_type = #{sourceType})
              and (#{feedback} is null or feedback = #{feedback})
              and (
                  #{keyword} is null
                  or lower(question) like lower(concat('%', #{keyword}, '%'))
                  or lower(answer) like lower(concat('%', #{keyword}, '%'))
                  or lower(coalesce(source_title, '')) like lower(concat('%', #{keyword}, '%'))
              )
            """)
    Long countPage(@Param("keyword") String keyword,
                   @Param("sourceType") String sourceType,
                   @Param("feedback") String feedback);

    @Select("""
            select * from ai_question_logs
            where source_type = 'NONE'
              and (
                  #{keyword} is null
                  or lower(question) like lower(concat('%', #{keyword}, '%'))
                  or lower(answer) like lower(concat('%', #{keyword}, '%'))
              )
            order by created_time desc
            limit #{size} offset #{offset}
            """)
    List<AiQuestionLog> findKnowledgeGapsPage(@Param("keyword") String keyword,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    @Select("""
            select count(*) from ai_question_logs
            where source_type = 'NONE'
              and (
                  #{keyword} is null
                  or lower(question) like lower(concat('%', #{keyword}, '%'))
                  or lower(answer) like lower(concat('%', #{keyword}, '%'))
              )
            """)
    Long countKnowledgeGapsPage(@Param("keyword") String keyword);

    @Select("select * from ai_question_logs where id = #{id}")
    AiQuestionLog findById(Long id);

    @Delete("delete from ai_question_logs where id = #{id}")
    void deleteById(Long id);

    @Update("""
            update ai_question_logs
            set feedback = #{feedback},
                feedback_comment = #{feedbackComment},
                feedback_time = CURRENT_TIMESTAMP
            where id = #{id}
            """)
    void updateFeedback(@Param("id") Long id,
                        @Param("feedback") String feedback,
                        @Param("feedbackComment") String feedbackComment);

    @Select("select count(*) from ai_question_logs")
    Long countAll();

    @Select("select count(*) from ai_question_logs where created_ticket_id is not null")
    Long countCreatedTickets();

    @Select("select count(*) from ai_question_logs where feedback = 'USEFUL'")
    Long countUsefulFeedback();

    @Select("select count(*) from ai_question_logs where feedback = 'NOT_USEFUL'")
    Long countNotUsefulFeedback();

    @Select("select count(*) from ai_question_logs where feedback is null")
    Long countPendingFeedback();

    @Select("""
    select * from ai_question_logs
    where source_type = 'NONE'
    order by created_time desc
    """)
    List<AiQuestionLog> findKnowledgeGaps();

    @Update("""
        update ai_question_logs
        set source_article_id = #{sourceArticleId},
            source_title = #{sourceTitle}
        where id = #{id}
        """)
    void linkKnowledgeArticle(@Param("id") Long id,
                              @Param("sourceArticleId") Long sourceArticleId,
                              @Param("sourceTitle") String sourceTitle);


    @Select("select count(*) from ai_question_logs where source_type = 'NONE'")
    Long countKnowledgeGaps();

    @Select("select count(*) from ai_question_logs where source_type in ('DOCUMENT', 'ARTICLE')")
    Long countAnsweredByInternalKnowledge();

    @Select("select count(*) from ai_question_logs where source_type = 'DOCUMENT'")
    Long countAnsweredByDocument();

    @Select("select count(*) from ai_question_logs where source_type = 'ARTICLE'")
    Long countAnsweredByArticle();

    @Select("select count(*) from ai_question_logs where source_type = 'NONE'")
    Long countUnansweredQuestions();
}
