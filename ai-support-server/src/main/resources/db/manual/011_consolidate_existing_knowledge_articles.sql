DO $$
DECLARE
    category_record RECORD;
    keeper_id BIGINT;
    merged_title TEXT;
    merged_content TEXT;
BEGIN
    FOR category_record IN
        SELECT category
        FROM knowledge_articles
        WHERE status = 'PUBLISHED'
          AND category IS NOT NULL
        GROUP BY category
        HAVING COUNT(*) > 1
    LOOP
        SELECT id
        INTO keeper_id
        FROM knowledge_articles
        WHERE status = 'PUBLISHED'
          AND category = category_record.category
        ORDER BY length(content) DESC, updated_time DESC, id ASC
        LIMIT 1;

        merged_title := CASE category_record.category
            WHEN 'Auth / JWT' THEN 'CloudOps Auth and JWT Knowledge Pack'
            WHEN 'Redis Cache' THEN 'CloudOps Redis Cache Knowledge Pack'
            WHEN 'PostgreSQL' THEN 'CloudOps PostgreSQL Knowledge Pack'
            WHEN 'Docker' THEN 'CloudOps Docker Knowledge Pack'
            WHEN 'Kubernetes' THEN 'CloudOps Kubernetes Knowledge Pack'
            WHEN 'Incident SOP' THEN 'CloudOps Incident Response Knowledge Pack'
            WHEN 'Ticket Rules' THEN 'CloudOps Ticket Operations Knowledge Pack'
            WHEN 'Customer Templates' THEN 'CloudOps Knowledge Governance and Customer Communication Pack'
            ELSE 'CloudOps ' || category_record.category || ' Knowledge Pack'
        END;

        SELECT '# ' || merged_title || E'\n\n'
               || 'Category: ' || category_record.category || E'\n'
               || 'Purpose: Consolidated reviewed internal knowledge for this CloudOps domain. This article is the durable answer source; uploaded document chunks remain retrieval evidence only.' || E'\n\n'
               || string_agg(
                    '## ' || title || E'\n\n' || trim(content),
                    E'\n\n---\n\n'
                    ORDER BY length(content) DESC, updated_time DESC, id ASC
               )
        INTO merged_content
        FROM knowledge_articles
        WHERE status = 'PUBLISHED'
          AND category = category_record.category;

        UPDATE knowledge_articles
        SET title = merged_title,
            content = merged_content,
            updated_time = CURRENT_TIMESTAMP
        WHERE id = keeper_id;

        UPDATE knowledge_articles
        SET status = 'ARCHIVED',
            updated_time = CURRENT_TIMESTAMP
        WHERE status = 'PUBLISHED'
          AND category = category_record.category
          AND id <> keeper_id;
    END LOOP;
END $$;
