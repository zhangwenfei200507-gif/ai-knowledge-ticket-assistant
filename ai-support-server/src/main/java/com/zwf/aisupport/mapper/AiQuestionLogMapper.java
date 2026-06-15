package com.zwf.aisupport.mapper;

import com.zwf.aisupport.entity.AiQuestionLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiQuestionLogMapper {

    @Insert("""
            insert into ai_question_logs (
                user_id,
                question,
                answer,
                source_article_id,
                source_title,
                suggest_create_ticket,
                created_ticket_id
            )
            values (
                #{userId},
                #{question},
                #{answer},
                #{sourceArticleId},
                #{sourceTitle},
                #{suggestCreateTicket},
                #{createdTicketId}
            )
            """)
    void insert(AiQuestionLog aiQuestionLog);
}