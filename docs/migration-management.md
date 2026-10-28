# Controlled Schema Migration

The project historically used ordered SQL files under `docs/sql`. The application now has an
optional Flyway control plane for migrations after version 23; it is disabled by default.

## Existing database

1. Export a schema and data backup.
2. Review and apply `docs/sql/022_add_ai_question_retrieval_trace.sql` and
   `docs/sql/023_create_web_evidence_sources.sql` in order if they are not already applied.
3. Create a Flyway baseline at version `23` after confirming those two changes are present.
4. Set `DB_MIGRATION_ENABLED=true` and `DB_MIGRATION_BASELINE_ON_MIGRATE=false`.
5. Flyway will validate the baseline and apply `V024__add_ai_observability_and_web_source_metadata.sql` once.

## New database

Use the existing bootstrap schema and ordered SQL history first, then baseline Flyway at version
23. A future release should convert the complete historical bootstrap into a reviewed baseline
artifact before enabling automatic migration on an empty database.

## Schema compatibility

`APP_SCHEMA_VALIDATION_MODE=WARN` reports drift without modifying data. After the migration is
confirmed in an isolated environment, deployments may use `FAIL` to stop startup before serving
traffic when required columns are missing. The checker never creates or drops schema objects.

No migration is run automatically in the current modification phase.
