package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.AiQuestionLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

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
}