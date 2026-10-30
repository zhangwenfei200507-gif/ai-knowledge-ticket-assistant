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
            answer_mode,
            answer_engine,
            internal_attempted,
            internal_sufficient,
            web_attempted,
            web_succeeded,
            web_sufficient,
            llm_attempted,
            llm_succeeded,
            retrieval_failure_reason,
            total_latency_ms,
            retrieval_trace_json,
            llm_provider,
            llm_model,
            llm_input_tokens,
            llm_output_tokens,
            llm_total_tokens,
            llm_estimated_cost,
            llm_currency,
            llm_usage_estimated,
            llm_retry_count,
            llm_latency_ms,
            llm_request_status,
            llm_error_category,
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
            #{answerMode},
            #{answerEngine},
            #{internalAttempted},
            #{internalSufficient},
            #{webAttempted},
            #{webSucceeded},
            #{webSufficient},
            #{llmAttempted},
            #{llmSucceeded},
            #{retrievalFailureReason},
            #{totalLatencyMs},
            cast(#{retrievalTraceJson} as jsonb),
            #{llmProvider},
            #{llmModel},
            #{llmInputTokens},
            #{llmOutputTokens},
            #{llmTotalTokens},
            #{llmEstimatedCost},
            #{llmCurrency},
            #{llmUsageEstimated},
            #{llmRetryCount},
            #{llmLatencyMs},
            #{llmRequestStatus},
            #{llmErrorCategory},
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
            where (cast(#{sourceType} as varchar) is null or source_type = #{sourceType})
              and (cast(#{feedback} as varchar) is null or feedback = #{feedback})
              and (
                  cast(#{keyword} as varchar) is null
                  or lower(question) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(answer) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(coalesce(source_title, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
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
            where (cast(#{sourceType} as varchar) is null or source_type = #{sourceType})
              and (cast(#{feedback} as varchar) is null or feedback = #{feedback})
              and (
                  cast(#{keyword} as varchar) is null
                  or lower(question) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(answer) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(coalesce(source_title, '')) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              )
            """)
    Long countPage(@Param("keyword") String keyword,
                   @Param("sourceType") String sourceType,
                   @Param("feedback") String feedback);

    @Select("""
            select * from ai_question_logs
            where source_type = 'NONE'
              and (cast(#{status} as varchar) is null
                   or (#{status} = 'OPEN' and feedback is null)
                   or (#{status} = 'REVIEWED' and feedback is not null))
              and (cast(#{category} as varchar) is null
                   or (#{category} = 'Auth / JWT' and lower(question) ~ '(jwt|oauth|auth|token|login)')
                   or (#{category} = 'Redis Cache' and lower(question) ~ '(redis|cache|keyspace|sentinel)')
                   or (#{category} = 'PostgreSQL' and lower(question) ~ '(postgres|postgresql|database|sql|wal|replication)')
                   or (#{category} = 'Docker' and lower(question) ~ '(docker|container|compose|buildkit)')
                   or (#{category} = 'Kubernetes' and lower(question) ~ '(kubernetes|k8s|pod|cluster|ingress)')
                   or (#{category} = 'Incident SOP' and lower(question) ~ '(incident|outage|rollback|postmortem|mitigation)')
                   or (#{category} = 'Ticket Rules' and lower(question) ~ '(ticket|sla|priority|triage|escalation)')
                   or (#{category} = 'Customer Templates' and lower(question) ~ '(customer|template|reply|communication)')
                   or (#{category} = 'General' and lower(question) !~ '(jwt|oauth|auth|token|login|redis|cache|keyspace|sentinel|postgres|database|sql|wal|replication|docker|container|compose|buildkit|kubernetes|k8s|pod|cluster|ingress|incident|outage|rollback|postmortem|mitigation|ticket|sla|priority|triage|escalation|customer|template|reply|communication)'))
              and (
                  cast(#{keyword} as varchar) is null
                  or lower(question) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(answer) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              )
            order by created_time desc
            limit #{size} offset #{offset}
            """)
    List<AiQuestionLog> findKnowledgeGapsPage(@Param("keyword") String keyword,
                                              @Param("category") String category,
                                              @Param("status") String status,
                                              @Param("offset") int offset,
                                              @Param("size") int size);

    @Select("""
            select count(*) from ai_question_logs
            where source_type = 'NONE'
              and (cast(#{status} as varchar) is null
                   or (#{status} = 'OPEN' and feedback is null)
                   or (#{status} = 'REVIEWED' and feedback is not null))
              and (cast(#{category} as varchar) is null
                   or (#{category} = 'Auth / JWT' and lower(question) ~ '(jwt|oauth|auth|token|login)')
                   or (#{category} = 'Redis Cache' and lower(question) ~ '(redis|cache|keyspace|sentinel)')
                   or (#{category} = 'PostgreSQL' and lower(question) ~ '(postgres|postgresql|database|sql|wal|replication)')
                   or (#{category} = 'Docker' and lower(question) ~ '(docker|container|compose|buildkit)')
                   or (#{category} = 'Kubernetes' and lower(question) ~ '(kubernetes|k8s|pod|cluster|ingress)')
                   or (#{category} = 'Incident SOP' and lower(question) ~ '(incident|outage|rollback|postmortem|mitigation)')
                   or (#{category} = 'Ticket Rules' and lower(question) ~ '(ticket|sla|priority|triage|escalation)')
                   or (#{category} = 'Customer Templates' and lower(question) ~ '(customer|template|reply|communication)')
                   or (#{category} = 'General' and lower(question) !~ '(jwt|oauth|auth|token|login|redis|cache|keyspace|sentinel|postgres|database|sql|wal|replication|docker|container|compose|buildkit|kubernetes|k8s|pod|cluster|ingress|incident|outage|rollback|postmortem|mitigation|ticket|sla|priority|triage|escalation|customer|template|reply|communication)'))
              and (
                  cast(#{keyword} as varchar) is null
                  or lower(question) like lower(concat('%', cast(#{keyword} as varchar), '%'))
                  or lower(answer) like lower(concat('%', cast(#{keyword} as varchar), '%'))
              )
            """)
    Long countKnowledgeGapsPage(@Param("keyword") String keyword,
                                @Param("category") String category,
                                @Param("status") String status);

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

    @Select("""
        select * from ai_question_logs
        where source_type in ('NONE', 'WEB', 'LLM', 'ARTICLE', 'DOCUMENT')
          and (
              source_type in ('NONE', 'WEB', 'LLM')
              or feedback = 'NOT_USEFUL'
          )
        order by created_time desc
        limit #{limit}
        """)
    List<AiQuestionLog> findStewardCandidates(int limit);
}
