# AegisCloud Backend Open Issues

Date: 2026-10-27

This document records only currently open or not-yet-verified backend and knowledge-system issues. Docker Desktop recovery is intentionally excluded.

## Current Verification Baseline

- OpenAI credentials: authorized successfully against the configured `gpt-5-nano` model.
- Backend unit tests: 61 total, 60 passed, 1 failed.
- Database-backed integration tests: not run for the latest changes.
- Four-route live evaluation: not rerun after the latest retrieval changes.
- Latest changes: not committed or pushed.

## P0 - Must Fix Before Runtime Acceptance

### 1. Cross-domain internal retrieval can incorrectly fall through to NONE

The current failing regression combines Docker networking and PostgreSQL connectivity knowledge. Relevant internal articles exist, but the router does not mark the aggregated evidence as a usable internal answer and eventually returns `NONE`.

This is a general aggregation defect, not a Docker-specific answer defect. The fix must ensure that:

- each required domain contributes evidence;
- coverage is evaluated across the selected evidence set;
- a source-backed answer can be composed without generic filler;
- failure in one presentation step does not silently discard otherwise sufficient evidence;
- partial evidence still proceeds to Web or LLM with an accurate failure reason.

### 2. New database migrations are not yet runtime-verified

The latest backend code requires:

- `022_add_ai_question_retrieval_trace.sql`
- `023_create_web_evidence_sources.sql`

Until these migrations are applied, question logging and multi-source Web Evidence persistence can fail with SQL column/table errors.

### 3. Retrieval trace is implemented but not verified end to end

The API and log entity now support:

- final answer mode;
- answer engine;
- internal retrieval attempted/sufficient;
- Web attempted/found/sufficient;
- LLM attempted/succeeded;
- failure reason.

The fields compile and unit tests pass, but they have not yet been verified against the real database or displayed in AI Activity.

### 4. Fixed four-route evaluation has not been rerun

The required INTERNAL_RAG, WEB_SEARCH, AI_ASSISTED_WEB, and AI_ONLY test suite must be rerun after the cross-domain regression is fixed. Route labels alone are not enough; answers must be graded for correctness, coverage, citations, and unsupported claims.

## P1 - Retrieval and Knowledge Quality

### 5. Existing Knowledge Packs are not fully audited against `rule.md`

The database previously contained 24 articles and 374 article chunks, including archived history. The active packs and chunks have not yet received a complete content-by-content verification for:

- duplicated explanations;
- residual Runbook/SOP/Title/Purpose text;
- incorrect category placement;
- fragmented topics;
- obsolete claims;
- weak or missing references;
- semantic chunk boundaries.

### 6. Retrieval is still primarily lexical

The current implementation has stronger query understanding, domain constraints, section-aware chunk scoring, and evidence coverage checks. It still lacks an evaluated production implementation of:

- vector retrieval;
- hybrid fusion;
- reranking;
- permission-aware vector filtering;
- offline Recall@K/MRR/nDCG evaluation.

### 7. Web Search reliability remains incomplete

Multi-query rewriting, source ranking, official-source preference, page cleaning, and multi-source preservation now exist in code. Remaining gaps:

- no configured reliable Search API provider was verified;
- DuckDuckGo HTML remains only a fragile fallback;
- live timeout, 403, 429, empty-result, and DNS failure tests are pending;
- multi-source storage has not been verified against the real database;
- source freshness and publication-date handling remain limited.

### 8. Existing review and evidence records still require lifecycle audit

Pending, rejected, converted, and archived records need verification for:

- duplicate candidates;
- invalid or low-quality evidence;
- correct source origin;
- deletion/archive rules;
- review audit history;
- correct reorganization into an existing Knowledge Pack;
- chunk rebuild after approval.

## P1 - Answer Generation and Model Use

### 9. Deterministic answer composition is still brittle

Generic hard-coded troubleshooting and decision text is being removed. The remaining deterministic composer must be isolated and tested so it only uses grounded evidence and never invents checks, fixes, priority rules, or operational guarantees.

### 10. LLM connectivity is verified externally, not through a live backend request

The configured OpenAI key and `gpt-5-nano` model are valid. The backend must be restarted to inherit the updated user environment, then the LLM status endpoint and one controlled grounded request must be verified.

### 11. LLM failure detail is not fully persisted per request

The trace records high-level failure categories such as `LLM_NOT_CONFIGURED` and `LLM_NO_USABLE_RESPONSE`. It does not yet persist the exact safe provider failure classification for timeout, rate limit, invalid model, empty response, or network failure alongside each question log.

### 12. Token and cost observability is incomplete

The system limits source count and evidence length, but per-request input tokens, output tokens, estimated cost, retry count, and model latency are not yet fully persisted and exposed in AI Activity.

## P2 - Architecture and Maintainability

### 13. `AiAssistantServiceImpl` remains too large

Query understanding and coverage evaluation have been extracted, reducing duplication, but the service still combines:

- routing;
- article retrieval;
- document retrieval;
- Web fallback;
- LLM fallback;
- answer composition;
- log management;
- knowledge gap management;
- ticket creation.

It should be split into focused services after behavior is protected by regression tests.

### 14. Schema migration remains manual

SQL files are versioned, but the application does not automatically track and apply migrations. This creates schema-drift risk and has already caused missing-column failures. A controlled migration mechanism is still needed.

### 15. Admin frontend does not yet expose the new backend detail

AI Activity and Web Evidence need to display:

- actual answer mode and engine;
- retrieval trace stages;
- failure reason;
- all Web sources, not only the primary URL;
- token/cost data when available.

## Pending Test Work

- Fix the single failing cross-domain regression.
- Run the complete backend suite with zero failures.
- Apply migrations 022 and 023 to an isolated database.
- Run all 18 fixed RAG questions and manually grade answers.
- Run document upload, review approval, Web Evidence conversion, knowledge-gap, and ticket lifecycle tests.
- Run permission isolation and prompt-injection tests.
- Run OpenAI timeout, rate-limit, invalid-model, and empty-response tests.
- Run browser/API regression for AI Activity and Web Evidence after backend fields are exposed.
- Run controlled 10/50/100 concurrency tests only against an isolated environment.

## Acceptance Rule

The backend is not ready to be marked PASS until:

1. all automated backend tests pass;
2. the migrations work on an isolated database;
3. the four answer routes are observable and reproducible;
4. the fixed evaluation set meets answer-quality and citation requirements;
5. knowledge approval reorganizes existing packs without duplication;
6. no unsupported claim is presented as grounded fact.
