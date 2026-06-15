package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.AiQuestionLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AiQuestionLogMapper {

    @Insert("""
            insert into ai_question_logs (
                user_id, question, answer, source_article_id, source_title,
                suggest_create_ticket, created_ticket_id
            )
            values (
                #{userId}, #{question}, #{answer}, #{sourceArticleId}, #{sourceTitle},
                #{suggestCreateTicket}, #{createdTicketId}
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

    @Select("select * from ai_question_logs where id = #{id}")
    AiQuestionLog findById(Long id);

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

    @Select("select count(*) from ai_question_logs where source_article_id is not null")
    Long countAnsweredByKnowledgeBase();

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
        where source_article_id is null
        order by created_time desc
        """)
    List<AiQuestionLog> findKnowledgeGaps();

}