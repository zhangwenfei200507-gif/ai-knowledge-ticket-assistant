# AI Answer Quality Audit - 2026-06-29

## Goal

This audit checks the current retrieval and answer pipeline after section-level article chunk retrieval was introduced.

The target behavior is:

- Prefer internal Knowledge Packs when they actually cover the user's core concepts.
- Use Web Search when internal knowledge is missing or only partially covers the concept.
- Avoid copying Knowledge Pack, chunk, or web evidence text directly into the user answer.
- Keep Web Evidence as reviewed knowledge candidates, not as automatically trusted articles.

## Current Implementation Status

Implemented in this round:

- Article chunk retrieval now scores by relevance, concept coverage, and question intent.
- Internal answers reject loosely related chunks when required concepts are missing.
- Technical terms such as `HTTPRoute`, `pg_stat_io`, `.dockerignore`, `ARG`, `ENV`, and `BuildKit` are preserved as answer concepts.
- Web evidence generation now creates cleaner Knowledge Pack candidate sections instead of raw page fragments.
- Web source filtering requires stronger question coverage and prefers official documentation.
- Regression tests cover internal answer routing, web fallback, answer cleanliness, and misrouting cases.

Not fully verified in a running environment:

- Database seeding for the new Web Evidence candidates.
- Browser/API end-to-end answers from the live backend.

Reason: Docker Desktop/PostgreSQL was not available during this audit, so database-backed endpoint testing could not be completed.

## Real Question Test Matrix

| Domain | Question | Expected Source | Verified By | Result |
|---|---|---|---|---|
| Auth / JWT | Why is my JWT rejected because of the `nbf` claim even though it has not expired? | WEB when internal article lacks `nbf` | Unit test | Pass. Related article alone is rejected; Web Evidence is used. |
| Docker | Why should Docker Compose services use service names instead of localhost for container-to-container communication? | ARTICLE | Unit test | Pass. Internal Docker article answers without Web fallback. |
| Docker | What is the purpose of Docker multi-stage builds, and when should they be used? | ARTICLE after approved knowledge exists; WEB before approval | Unit test currently validates WEB fallback | Pass as fallback. SQL seed adds official Docker candidate for review. |
| Docker | How does Docker BuildKit improve image build performance? | ARTICLE after approved knowledge exists; WEB before approval | Unit test currently validates WEB fallback | Pass as fallback. Answer keeps BuildKit performance concepts visible. |
| Docker | What is the difference between ARG and ENV in a Dockerfile, and why should I use a `.dockerignore` file? | ARTICLE after approved knowledge exists; WEB before approval | Unit test currently validates WEB fallback | Pass as fallback. Answer includes ARG, ENV, and `.dockerignore`. |
| Kubernetes | What is Kubernetes Gateway API HTTPRoute and when should it replace Ingress? | ARTICLE after approved knowledge exists | Unit test | Pass. Section-level retrieval chooses the HTTPRoute chunk over a generic Kubernetes chunk. |
| Redis Cache | How does Redis client-side caching with server-assisted tracking work? | ARTICLE after approved knowledge exists | Unit test | Pass. Section-level retrieval chooses client-side caching instead of generic Redis troubleshooting. |
| Ticket Rules | How should I determine whether a support ticket is P1, P2, or P3? | ARTICLE | Unit test | Pass. Internal ticket priority knowledge is used without falling back to None. |

## Added Knowledge Candidates

The SQL seed `docs/sql/017_seed_core_web_evidence_candidates.sql` adds reviewed-source candidates for topics that should become internal knowledge after admin approval:

- Docker Bake from official Docker documentation.
- Kubernetes ValidatingAdmissionPolicy from official Kubernetes documentation.
- PostgreSQL logical replication failover slots from official PostgreSQL documentation.
- Redis keyspace notifications from official Redis documentation.

These records enter `Web Evidence` as `NEEDS_REVIEW` and `WEB_SEARCH`, so they must still be approved before becoming Knowledge Pack content.

## Remaining Risk

- Without a configured LLM, answer synthesis is still deterministic and cannot reach the final product quality target for every possible technical question.
- Search quality still depends on the configured search provider. The code supports a provider-backed path, but live verification requires the backend, database, and provider key to be available.
- Existing production database content must be rebuilt into article chunks after migrations are applied.

## Required Live Verification

After Docker Desktop and the backend database are running:

1. Apply `docs/sql/016_create_knowledge_article_chunks.sql`.
2. Apply `docs/sql/017_seed_core_web_evidence_candidates.sql`.
3. Restart the backend.
4. Rebuild Knowledge Pack chunks from the admin maintenance action.
5. Run `docs/eval/run-ai-answer-quality-audit.ps1`.

The live audit should compare expected source vs actual source and mark each answer with `PASS`, `ROUTING_FAIL`, `COVERAGE_FAIL`, `SYNTHESIS_FAIL`, `NOISE_FAIL`, or `SEARCH_FAIL`.
