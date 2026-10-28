package com.zwf.aisupport.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reports schema drift without silently changing the database. Failing startup is opt-in so a
 * deployment can first apply the controlled migration and then switch the mode to FAIL.
 */
@Component
public class SchemaCompatibilityChecker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompatibilityChecker.class);

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;
    private final String mode;

    public SchemaCompatibilityChecker(JdbcTemplate jdbcTemplate,
                                       @Value("${app.schema.validation.enabled:true}") boolean enabled,
                                       @Value("${app.schema.validation.mode:WARN}") String mode) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
        this.mode = mode == null ? "WARN" : mode.trim().toUpperCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        List<String> missing;
        try {
            missing = List.of(
                    "ai_question_logs.answer_mode",
                    "ai_question_logs.answer_engine",
                    "ai_question_logs.retrieval_trace_json",
                    "web_evidence_sources.source_type"
            ).stream()
                    .filter(value -> !columnExists(value.substring(0, value.indexOf('.')), value.substring(value.indexOf('.') + 1)))
                    .toList();
        } catch (DataAccessException exception) {
            String message = "Database schema compatibility check could not reach the database; "
                    + "startup continues in " + mode + " mode and database-backed requests remain unavailable until connectivity is restored.";
            if ("FAIL".equals(mode)) {
                throw new IllegalStateException(message, exception);
            }
            log.warn(message);
            return;
        }
        if (missing.isEmpty()) {
            log.info("Database schema compatibility check passed");
            return;
        }
        String message = "Database schema is missing required columns: " + String.join(", ", missing)
                + ". Apply the controlled migrations before enabling production traffic.";
        if ("FAIL".equals(mode)) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = current_schema()
                  and table_name = ?
                  and column_name = ?
                """, Integer.class, table, column);
        return count != null && count > 0;
    }
}
