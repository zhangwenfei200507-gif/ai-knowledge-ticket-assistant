UPDATE knowledge_articles
SET title = CASE
        WHEN title ILIKE '%Knowledge Pack%' THEN title
        ELSE title || ' Knowledge Pack'
    END,
    content = '# ' || CASE
            WHEN title ILIKE '%Knowledge Pack%' THEN title
            ELSE title || ' Knowledge Pack'
        END || E'\n\n'
        || 'Category: ' || COALESCE(NULLIF(category, ''), 'General') || E'\n\n'
        || '## Overview' || E'\n'
        || 'This Knowledge Pack covers ' || COALESCE(NULLIF(category, ''), 'General')
        || ' knowledge for AegisCloud support. It is maintained as a durable, low-redundancy source for AI retrieval and administrator review.'
        || E'\n\n'
        || '## Core Concepts' || E'\n'
        || regexp_replace(
            regexp_replace(
                regexp_replace(content, '(?im)^#\s+.*$', '', 'g'),
                '(?im)^Category\s*:.*$', '', 'g'
            ),
            E'\\n{3,}',
            E'\\n\\n',
            'g'
        )
        || E'\n\n'
        || '## Best Practices' || E'\n'
        || '- Keep this Knowledge Pack focused, deduplicated, and easy to retrieve.' || E'\n'
        || '- Update the closest existing Knowledge Pack first; create a new pack only for a truly new domain.' || E'\n\n'
        || '## References' || E'\n'
        || '- Source: Existing article reorganized by Knowledge Library rule.' || E'\n'
        || '- Chunk rule: after content changes, retrieval chunks should be reorganized by semantic knowledge point.',
    updated_time = CURRENT_TIMESTAMP
WHERE status = 'PUBLISHED'
  AND (
      title NOT ILIKE '%Knowledge Pack%'
      OR content NOT ILIKE '%## Overview%'
      OR content NOT ILIKE '%## Core Concepts%'
      OR content NOT ILIKE '%## References%'
  );
