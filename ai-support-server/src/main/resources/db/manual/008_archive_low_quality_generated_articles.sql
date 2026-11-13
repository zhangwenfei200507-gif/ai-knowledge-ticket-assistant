UPDATE knowledge_articles
SET status = 'ARCHIVED',
    updated_time = CURRENT_TIMESTAMP
WHERE status = 'PUBLISHED'
  AND (
      content ILIKE '%I could not find a reliable answer%'
      OR content ILIKE '%Add concrete checks, commands, dashboards, or logs%'
      OR content ILIKE '%Add the final reviewed answer here before publishing%'
  );
