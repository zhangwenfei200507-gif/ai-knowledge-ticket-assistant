# AI Answer Quality Audit - 2026-06-24

## Goal

This audit checks whether the assistant behaves like an enterprise AI knowledge platform instead of a keyword search UI.

The focus is not a single Docker or JWT question. The focus is the whole quality system:

- Article quality
- Answer quality
- Web search quality
- Coverage routing between internal knowledge, web evidence, and model fallback

## Current Result

The latest code removes many obvious answer-noise problems:

- Internal titles are no longer intentionally used as answers.
- `Primary keywords`, `Source references`, review notes, script fragments, and fenced command blocks are filtered before answer generation.
- Technical identifiers such as `pg_stat_io` are kept as one concept instead of being split into weak tokens.
- Web search now creates official-domain queries for common CloudOps categories.
- The answer flow tries configured LLM grounded generation before falling back to local deterministic templates.

However, the system is still not at the final product target when no LLM API is configured.

## Test Matrix

| Domain | Test Type | Question | Expected Answer Shape | Actual Source | Current Assessment |
|---|---|---|---|---|---|
| Auth / JWT | Internal | What should I check when a Spring Security JWT API returns 401 or 403? | Explain 401 vs 403, token validity, issuer/audience, role mapping, and security config. | DOCUMENT | Partial. It no longer exposes major metadata, but routing should prefer the Auth Knowledge Pack, not document chunks. |
| Auth / JWT | Web | What is JWT DPoP proof-of-possession and how is it different from a bearer token? | Explain key-bound proof, per-request proof JWT, and why bearer tokens are weaker if stolen. | NONE | Fail. Current free web scraping did not retrieve reliable evidence. Needs stronger Search Agent or LLM fallback. |
| Docker | Internal | Why should Docker Compose services use service names instead of localhost for container-to-container communication? | Explain container-local `localhost`, Compose DNS, service names, and internal ports. | ARTICLE | Passable. Noise is reduced, but the local template is still less fluent than a model-generated answer. |
| Docker | Web | What is the difference between ARG and ENV in a Dockerfile, and why should I use a .dockerignore file? | Explain build-time vs runtime variables and build-context filtering. | NONE | Fail after stricter evidence checks. Needs multi-source official Docker retrieval or LLM fallback. |
| Incident SOP | Internal | How should I classify an incident as P1, P2, or P3? | Classify by impact, urgency, production risk, security/data loss, scope, and workaround. | ARTICLE | Passable. The decision template is cleaner, but should be more concrete and less generic. |
| Incident SOP | Web | What is the difference between SEV0 and SEV1 in incident management? | Explain highest severity vs major incident, with impact examples. | WEB | Partial. Web source found, but the answer still follows extracted source phrasing too closely. |
| Customer Templates | Internal | What should administrators check before publishing knowledge into the internal knowledge base? | Explain source quality, relevance, category, no secrets, structure, review status, and maintainability. | ARTICLE | Partial. Internal source is used, but article quality is too generic and needs Knowledge Pack restructuring. |
| Customer Templates | Web | How do RAG teams measure retrieval quality with precision at k and recall at k? | Explain precision@k and recall@k clearly with short examples. | NONE | Fail. This topic falls outside the current CloudOps source routing and needs model fallback or a search provider. |
| Kubernetes | Internal | What is the difference between readiness, liveness, and startup probes in Kubernetes? | Explain traffic gating, restart behavior, and slow-start protection. | ARTICLE | Partial. Finds internal article but still needs better section-aware synthesis. |
| Kubernetes | Web | What is Kubernetes Gateway API HTTPRoute and when should it replace Ingress? | Explain HTTPRoute, Gateway API roles, routing features, and migration criteria. | WEB | Partial. Evidence is found, but intent classification and final answer need stronger model synthesis. |
| PostgreSQL | Internal | What should I check when PostgreSQL connections time out or the pool is exhausted? | Explain connectivity, credentials, pool limits, max connections, locks, slow queries, and timeouts. | ARTICLE | Passable. Needs more concise direct answer. |
| PostgreSQL | Web | What does pg_stat_io show in PostgreSQL 16 and how is it useful? | Explain IO statistics by backend/object/context and diagnosis value. | NONE | Fail. Identifier parsing is fixed, but current search did not retrieve stable official evidence. |
| Redis Cache | Internal | What should I check when Redis latency spikes or keys disappear? | Explain latency, memory pressure, eviction, TTL, slowlog, persistence, and recent changes. | NONE | Fail. Coverage evaluation is still too strict or the Redis article lacks a clean section covering both concepts. |
| Redis Cache | Web | How does Redis client-side caching with server-assisted tracking work? | Explain read tracking and invalidation messages. | NONE | Fail in the latest run. Needs stronger official Redis retrieval or LLM fallback. |
| Ticket Rules | Internal | How should I determine whether a support ticket is P1, P2, or P3? | Explain P1/P2/P3 by impact, risk, scope, workaround, and SLA. | ARTICLE | Passable. Decision routing works, but answer should include the actual P1/P2/P3 table. |
| Ticket Rules | Web | How does an ITIL impact and urgency matrix calculate incident priority? | Explain impact multiplied/combined with urgency through a priority matrix. | ARTICLE | Fail. Internal ticket rules were overused for an external ITIL-specific question. |

## Root Causes

### P0 - Local template generation is not enough

Without a configured LLM, the backend can filter and rearrange text, but it cannot reliably synthesize a polished enterprise answer across all topics.

Required fix:

- Use grounded LLM generation when internal knowledge or web evidence is available.
- Keep deterministic templates only as a fallback.
- Add an answer verifier that checks whether the final answer satisfies the user question.

### P0 - Retrieval is not section-aware

Articles are stored as large Knowledge Pack text. The answer layer still has to infer relevant sections from flat content.

Required fix:

- Split each Knowledge Pack into semantic sections.
- Store section type, concept tags, and intent tags.
- Retrieve by `domain -> section -> chunk`, not by entire article text.

### P0 - Web Search needs a real Search Agent

DuckDuckGo HTML scraping is unstable. It can miss official pages or return tutorial fragments.

Required fix:

- Use a reliable search provider or API-backed search adapter.
- Keep official-domain query rewriting.
- Merge multiple sources only after each source passes relevance and quality checks.
- Route failed web searches to model fallback when configured.

### P1 - Article quality still needs reorganization

Some articles still contain remnants from imported runbooks, SOPs, and generated consolidation text.

Required fix:

- Rebuild Knowledge Packs into official-document style sections.
- Remove `Purpose`, repeated titles, source URL lists, runbook wording, and operational placeholders.
- Keep chunks as retrieval units, not mini-articles.

### P1 - Coverage needs two layers

The current coverage logic improved but still mixes retrieval terms and answer-required concepts.

Required fix:

- Retrieval terms decide what to search.
- Required concepts decide whether the answer covers the question.
- Final answer verification should check task completion, not just keyword overlap.

## Decision

The next major step should be:

1. Rebuild current Knowledge Packs into clean enterprise articles.
2. Add section-aware retrieval metadata.
3. Keep the current answer-cleaning gates.
4. Connect a real LLM for grounded answer synthesis.
5. Replace free HTML scraping with a real Search Agent when possible.

The current Java-only fallback is useful as a safety layer, but it cannot be the final AI layer for the quality target of this project.
