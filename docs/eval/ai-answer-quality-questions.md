# AI Answer Quality Evaluation Questions

This fixed question set is the baseline for AegisCloud answer-quality regression checks.

Use it after changes to article reorganization, chunk retrieval, web search, or answer generation. Do not replace the set just because one implementation fails; add new cases only when they represent a new product capability.

## Passing Criteria

An answer passes only when all of these are true:

- It directly answers the user question first.
- It does not expose article titles, runbook labels, chunk names, source metadata, or review workflow language.
- It uses internal Knowledge Packs when they clearly cover the question.
- It goes to Web Search when internal knowledge is missing or only partially covers the required concepts.
- It uses concise, professional language and avoids raw copied page text.
- It preserves grounding sources as evidence, not as the answer itself.

## Internal Knowledge Questions

| Domain | Question | Expected Source | Expected Answer Shape |
|---|---|---|---|
| Auth / JWT | What should I check when a Spring Security JWT API returns 401 or 403? | ARTICLE | Explain 401 vs 403, bearer token validity, issuer/audience, role mapping, user status, and Spring Security config. |
| Docker | Why should Docker Compose services use service names instead of localhost for container-to-container communication? | ARTICLE | Explain container-local localhost, Compose DNS, service names, shared network, and internal ports. |
| Docker | What is the purpose of Docker multi-stage builds, and when should they be used? | ARTICLE | Explain multiple FROM stages, build-stage isolation, smaller runtime images, and when to separate build and runtime dependencies. |
| Docker | How does Docker BuildKit improve image build performance? | ARTICLE | Explain concurrent build graph solving, cache behavior, skipped unused stages, and smaller build context transfer. |
| Docker | What is the difference between ARG and ENV in a Dockerfile, and why should I use a .dockerignore file? | ARTICLE | Explain build-time ARG, runtime/persistent ENV, build context filtering, faster builds, cache behavior, and secret/noise reduction. |
| Incident SOP | How should I classify an incident as P1, P2, or P3? | ARTICLE | Classify by impact, urgency, production risk, security/data-loss risk, scope, workaround, and update cadence. |
| Customer Templates | What should administrators check before publishing knowledge into the internal knowledge base? | ARTICLE | Explain source quality, domain match, no secrets, clear structure, grounding, review status, and maintainability. |
| Kubernetes | What is the difference between readiness, liveness, and startup probes in Kubernetes? | ARTICLE | Explain readiness traffic gating, liveness restarts, startup slow-start protection, and when each is used. |
| Kubernetes | What is Kubernetes Gateway API HTTPRoute and when should it replace Ingress? | ARTICLE | Explain HTTPRoute, Gateway API role separation, richer routing, cross-namespace attachment, and migration criteria. |
| PostgreSQL | What should I check when PostgreSQL connections time out or the pool is exhausted? | ARTICLE | Explain connectivity, credentials, pool limits, max connections, locks, slow queries, timeouts, and network path. |
| PostgreSQL | What does pg_stat_io show in PostgreSQL 16 and how is it useful? | ARTICLE | Explain IO statistics by backend/object/context and how it helps diagnose read/write/fsync/cache behavior. |
| Redis Cache | What should I check when Redis latency spikes or keys disappear? | ARTICLE | Explain latency, memory pressure, eviction, TTL, slowlog, blocked clients, persistence, and recent changes. |
| Redis Cache | How does Redis client-side caching with server-assisted tracking work? | ARTICLE | Explain client read tracking, invalidation messages, cache safety, and tradeoffs. |
| Ticket Rules | How should I determine whether a support ticket is P1, P2, or P3? | ARTICLE | Explain P1/P2/P3 using impact, urgency, scope, risk, workaround, and SLA. |

## Web Search Questions

| Domain | Question | Expected Source | Expected Answer Shape |
|---|---|---|---|
| Auth / JWT | What is JWT DPoP proof-of-possession and how is it different from a bearer token? | WEB | Explain key-bound proof, per-request proof JWT, replay resistance, and bearer-token theft risk. |
| Docker | What is Docker Bake and when should a team use it instead of plain docker build commands? | WEB | Explain build definitions, repeatable multi-target builds, CI usage, and when it is worth adopting. |
| Incident SOP | What is the difference between SEV0 and SEV1 in incident management? | WEB | Explain highest severity vs major incident and when escalation cadence changes. |
| Customer Templates | How do RAG teams measure retrieval quality with precision at k and recall at k? | WEB | Explain precision@k, recall@k, relevant top-k results, and why both are needed. |
| Kubernetes | What is the Kubernetes ValidatingAdmissionPolicy feature used for? | WEB | Explain admission-time validation, CEL policies, admission control, and policy use cases. |
| PostgreSQL | What is the purpose of PostgreSQL logical replication failover slots? | WEB | Explain logical replication slot continuity during failover and what operators should check. |
| Redis Cache | What is Redis keyspace notification and when should it be enabled? | WEB | Explain event notifications, configuration, operational tradeoffs, and why it should be enabled selectively. |
| Ticket Rules | How does an ITIL impact and urgency matrix calculate incident priority? | WEB | Explain combining impact and urgency through a priority matrix. |

## Failure Labels

- `ROUTING_FAIL`: wrong source chain, such as internal article used for a clearly external concept.
- `COVERAGE_FAIL`: source is related but does not cover the key concept.
- `SYNTHESIS_FAIL`: source is good but answer copies raw text or misses the direct answer.
- `NOISE_FAIL`: answer exposes metadata, runbook labels, titles, code fragments, or review workflow.
- `SEARCH_FAIL`: web search returns no reliable evidence for a question that should be searchable.
