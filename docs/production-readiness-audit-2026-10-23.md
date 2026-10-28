# AegisCloud Refactor Audit

**State:** Active implementation record, not a production acceptance report.
**Baseline:** Repository state inspected on 2026-10-23. Existing user changes and data were preserved; no commit, push, database reset, or live migration was performed.

## Architecture

- Backend: Java 17, Spring Boot 3.5, Spring Security OAuth2/JWT, MyBatis, PostgreSQL, Redis.
- Frontend: React 19 and Vite 7; admin operations currently live in a large `AdminOperationsCenter.jsx` module.
- Internal retrieval: PostgreSQL full-text search over article/document chunks, followed by application-level lexical relevance and answer-coverage checks. This is not vector hybrid search and has no embedding reranker.
- External retrieval: optional Serper provider and DuckDuckGo fallback, page extraction, URL policy checks, and evidence persistence.
- Answer generation: optional server-side OpenAI Responses API adapter. When configured, grounded evidence can be synthesized once; ungrounded fallback is separately labeled. `store:false` is sent. The API key is never sent to the browser.
- Knowledge lifecycle: document chunks, article chunks, web evidence, knowledge candidates, and Knowledge Steward review tasks are represented in the current implementation and SQL scripts.

## Implemented In This Refactor

### Authentication

- Replaced local username/password registration, login, and password-reset UI/API with Google and GitHub OAuth entry points.
- Only providers with both client ID and secret are advertised as available; the frontend disables unconfigured providers instead of pretending they work.
- Google sign-in requires a verified email. GitHub sign-in fetches and requires a verified email.
- A first OAuth sign-in creates a `USER`; matching a verified email links the provider identity to the existing account and preserves that account's existing role. A provider login cannot promote a new account to `ADMIN`.
- OAuth returns a short-lived, one-time Redis exchange code in the URL fragment. The frontend exchanges it for the existing JWT session, then removes the callback fragment. The transient OAuth session is invalidated after successful code issuance.
- OAuth identity and session-version changes are additive SQL migrations `020_create_user_oauth_identities.sql` and `019_add_user_session_version.sql`. They have **not** been applied to a database.

### Retrieval And Answers

- The route remains internal article chunks, legacy article fallback, permission-filtered document chunks, external web search, then eligible OpenAI general-knowledge fallback.
- Internal and web evidence can use a configured OpenAI model for grounded synthesis; when no model is configured, no failed provider call is attempted and the deterministic evidence-based response remains available.
- Answer generation includes bounded evidence, prompt-injection handling, server-verified references, and an explicit answer-engine marker.
- Removed per-topic phrase and side-concept allowlists from coverage and answer-point filtering. Query terms are normalized consistently, contextual names/timestamps are discounted, explicit uppercase identifiers are recognized, and answer points are screened using question-term relevance.
- Model requests are bounded and disable provider-side response storage. The API adapter has mocked success and rate-limit tests; it does not yet provide durable token/cost accounting, circuit breaking, retry/backoff, or streaming.

### User Experience And Hygiene

- Replaced the email/password sign-in UI with the two OAuth providers and a role-aware user workspace.
- The workspace accepts optional PDF, DOCX, TXT, and Markdown files within the existing 10 MiB limit, shows answer sources, renders Markdown/GFM safely, and supports copy.
- Added `react-markdown` and `remark-gfm`; fixed the reported frontend dependency advisories using compatible dependency updates.
- Updated README and the AI-answer audit script to describe OAuth-only sign-in and the actual PostgreSQL full-text retrieval behavior.

## Verification Matrix

| Check | Result |
|---|---|
| Backend clean test suite | PASS: 50 tests, 0 failures/errors, including AI routing, query normalization, OAuth, LLM adapter, and file limits. |
| Frontend production build | PASS: `npm.cmd run build` completed with Vite 7.3.7. |
| Frontend dependency audit | PASS: `npm audit --audit-level=low` reported zero known vulnerabilities. |
| Compose configuration | PASS: `docker compose config --quiet` with temporary validation-only environment values; no secrets were written. |
| Browser sign-in screen | PARTIAL: rendered and accessibility tree checked. The current API health route responds, but `/api/auth/oauth/providers` returns 403 from the backend process on port 8090, indicating it has not loaded this OAuth-enabled build. |
| Google/GitHub provider login | NOT VERIFIED: provider applications and client credentials are not configured in this workspace. |
| OAuth migrations 019/020 | NOT VERIFIED: not applied; back up the database and test on a disposable copy first. |
| Live OpenAI request | NOT VERIFIED: no API key is configured here. A previously pasted key was treated as exposed; revocation cannot be verified from this workspace. |
| Live search-provider request | NOT VERIFIED: provider credentials/network behavior were not exercised. |
| Browser E2E, accessibility, responsive breakpoints | NOT VERIFIED: no Playwright-style browser test suite is configured. |
| Populated-database lifecycle and migration rollback | NOT VERIFIED. |
| Production load, deployment, backup/restore drill | NOT VERIFIED. |

## Remaining Production Gates

1. Configure OAuth applications and exact callback URLs; apply migrations 019/020 only after a verified backup and a disposable-database rehearsal.
2. Replace browser `localStorage` JWT storage with a same-site, HTTP-only secure cookie or another XSS-resilient session design before public production deployment.
3. Add browser E2E coverage for OAuth success/failure, admin/user authorization, document Q&A, review actions, and the seven admin areas at desktop/tablet/mobile sizes.
4. Add durable AI request traces: source IDs, provider request ID, verification status, fallback reason, latency, token usage, and cost. Keep credentials and raw sensitive document content out of logs.
5. Evaluate lexical retrieval against a versioned test set. Vector retrieval/reranking remains a separate design decision requiring schema migration, embedding backfill, rollback, and measured quality gains; do not call current search “hybrid vector RAG.”
6. Validate live provider source quality and web fallback behavior, including redirects, timeouts, rate limits, stale content, duplicates, and provider outages.
7. Split the oversized admin React module only along stable page/API ownership boundaries, with interaction tests and no visual regression to existing user work.
8. Run populated-database acceptance, backup/restore, performance, accessibility, and production deployment checks before calling the system production-ready.

## Release Decision

**Not production-ready yet.** OAuth provider credentials, migrations, live OpenAI/search calls, browser acceptance, and operational recovery have not been verified. This report intentionally distinguishes implemented code from external setup and end-to-end acceptance.
