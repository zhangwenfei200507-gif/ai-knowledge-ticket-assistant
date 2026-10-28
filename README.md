# CloudOps AI Knowledge Support Platform

An enterprise-style AI knowledge support platform for CloudOps and B2B SaaS support teams.

The long-term product direction is clear: support engineers upload internal runbooks, SOPs, configuration notes, and troubleshooting guides; users ask operational questions; the system searches internal documents and knowledge articles first, uses trusted web evidence as a later-stage fallback when internal knowledge is insufficient, and creates support tickets when the issue still needs human handling.

The current backend provides OAuth sign-in, role-aware administration, uploaded knowledge sources, article packs and chunks, knowledge review, web evidence, tickets, and an answer path that prioritizes internal evidence before external search and configured OpenAI synthesis. Retrieval currently uses PostgreSQL full-text and lexical coverage logic; embeddings and vector reranking are not implemented.

## Product Direction

This project is not a simple ticket system, a simple CRUD knowledge base, or a thin chatbot wrapper.

It is designed for a CloudOps / SaaS support loop:

```text
Upload CloudOps runbooks, SOPs, and configuration notes
  -> Parse and store internal operational knowledge
  -> Ask questions about incidents, login failures, cache outages, or deployment issues
  -> Search internal documents and knowledge articles first
  -> Return grounded answers with traceable sources
  -> Use trusted web evidence when internal knowledge is insufficient
  -> Create support tickets when human handling is still required
  -> Record knowledge gaps and weak coverage areas
  -> Let administrators review evidence and improve the internal knowledge base
```

The goal is to make AI the first-response layer for CloudOps support, while keeping private internal context, human review, knowledge quality, and traceable sources at the center of the system.

## Target Enterprise Scenario

The project is modeled around a B2B SaaS / cloud-native operations support team.

Typical users are support engineers, platform engineers, or technical support staff who need to answer questions about:

- Login failures, JWT validation, and authentication flow issues.
- Redis cache outages, token storage problems, and session-state failures.
- PostgreSQL connection timeouts and database troubleshooting.
- Docker Compose local environments and container startup failures.
- Kubernetes deployment, scaling, and rollback issues.
- Production incident escalation, support ticket priority, and SLA handling.
- Customer-facing support responses and operational communication templates.

Internal knowledge is still essential because it contains private context that web search cannot know: service ownership, internal runbooks, environment configuration, escalation policy, customer support templates, and company-specific troubleshooting procedures.

Trusted web evidence is used to supplement internal knowledge with external technical facts, official documentation, and current best practices. It should be reviewed before being converted into reusable internal knowledge.

## Core Knowledge Scope

The internal knowledge base should be built around these essential CloudOps categories:

| Category | Example Knowledge |
| --- | --- |
| System Architecture | Service dependencies, Redis role, JWT login flow, PostgreSQL schema notes |
| Troubleshooting Runbooks | Login failure, Redis outage, JWT invalid token, database timeout, backend startup failure |
| Operational SOPs | Incident escalation, rollback procedure, service restart process, SLA and ownership rules |
| Configuration Reference | Local ports, Redis/PostgreSQL settings, Spring profiles, environment variables |
| Security And Permissions | JWT lifecycle, logout behavior, admin API access, role-based permissions |
| Support Communication | Customer response templates, outage updates, diagnostic information requests |
| Trusted External Sources | redis.io, docs.spring.io, postgresql.org, docs.docker.com, kubernetes.io, learn.microsoft.com |

Priority seed documents and articles:

```text
1. Redis Login Troubleshooting Guide
2. JWT Authentication Failure Runbook
3. PostgreSQL Connection Timeout Checklist
4. Docker Compose Local Environment Guide
5. Kubernetes Deployment Rollback Procedure
6. Production Incident Escalation Policy
7. Ticket Priority Classification Rules
8. Customer Response Template For Login Issues
9. Spring Security Permission Troubleshooting
10. Trusted External Sources For CloudOps Troubleshooting
```

## Project Highlights

- JWT authentication with Redis-backed token validation and logout.
- Google and GitHub OAuth sign-in, current-user query, and role-based access control.
- Ticket lifecycle management, including creation, assignment, resolution, and closure.
- Knowledge base article creation, search, update, archive, and category statistics.
- Uploaded document metadata, text chunking, keyword search, archive flow, and category stats.
- AI assistant API that answers from uploaded documents or knowledge articles and creates tickets when needed.
- AI question logging, answer feedback, dashboard statistics, and knowledge-gap discovery.
- Closed-loop knowledge improvement: unanswered AI questions can be converted into reusable knowledge articles.
- Docker Compose based local infrastructure with PostgreSQL and Redis.
- Clear layered backend structure: Controller -> Service -> Mapper -> Database.
- Internal-first answer routing with web fallback and optional OpenAI grounded synthesis.
- CloudOps-focused knowledge scope covering authentication, Redis, PostgreSQL, Docker, Kubernetes, incident SOPs, tickets, and trusted external sources.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Java 17 |
| Backend Framework | Spring Boot 3.5.15 |
| Frontend | Vite, JavaScript, CSS |
| Build Tool | Maven |
| Database | PostgreSQL |
| Cache / Login State | Redis |
| Persistence | MyBatis |
| Security | Spring Security, JWT |
| Local Infrastructure | Docker Compose |
| Version Control | Git, GitHub |

## Module Overview

### Current Phase

The current implementation searches internal document/article content before external web evidence. It does not use vector embeddings or a trained reranker; answer quality must be assessed with the repository evaluation set rather than described as perfect semantic retrieval.

### User And Authentication

- Google OpenID Connect and GitHub OAuth2 sign-in.
- Verified provider email is required; GitHub's verified-email endpoint is queried when needed.
- Existing users are linked by verified email without changing their role; new accounts are created as USER.
- Provider identities are stored separately from profile email, and OAuth callback codes are single-use, short-lived Redis values.
- Application JWTs are stored in Redis for revocation and current role/status are reloaded from PostgreSQL on protected requests.

### Ticket Workflow

- Create support tickets.
- Query tickets created by the current user.
- Query assigned tickets.
- Admin ticket list.
- Assign tickets to users.
- Resolve and close tickets.
- Basic status and priority workflow.

### Knowledge Base

- Create published knowledge articles.
- Search articles by title or content.
- View article detail.
- Update article content.
- Archive outdated articles.
- Count articles by category.

### Document Knowledge Base

- Upload `.txt` and `.md` documents.
- Store document metadata.
- Split document text into searchable chunks.
- Search active document chunks by keyword or full question text.
- View chunks for a document.
- Archive outdated documents.
- Count active documents by category.

### AI Assistant

- Ask AI based on uploaded documents first, then structured knowledge articles.
- Return source document chunk or source article information when an answer is found.
- Suggest ticket creation when no reliable answer exists.
- Automatically create tickets from unanswered questions.
- Generate AI-created ticket titles and priority.
- Record AI question logs.
- Collect feedback on AI answers.
- Report AI dashboard statistics.
- Identify knowledge gaps.
- Convert knowledge gaps into new knowledge articles.

### Current Document Knowledge Module

- Upload internal `.txt` and `.md` documents such as manuals, notes, FAQs, troubleshooting guides, and project documentation.
- Store document metadata.
- Parse document content into searchable chunks.
- Search document chunks before creating tickets.
- Return answers with document and chunk-level sources.
- Track whether AI answers came from documents, articles, or no internal source.

### Web-Augmented Answering

- Search trusted web sources when internal knowledge cannot answer a question.
- Return external source links for review.
- Record web search results.
- Allow administrators to convert useful external findings into internal knowledge articles.

### Admin Dashboard

- Total ticket count.
- Open and in-progress ticket count.
- Resolved and closed ticket count.
- Published knowledge article count.
- Active document count.
- Document answer count.
- Article answer count.
- AI question count.
- AI-created ticket count.
- Current knowledge-gap count.

## Local Development

### Project Structure

```text
ai-knowledge-ticket-assistant
|-- ai-support-server   Spring Boot backend API
|-- ai-support-web      Vite frontend workspace
|-- docs                SQL scripts and learning notes
|-- Pictures            UI reference images
|-- TestFiles           Local manual API test files
`-- docker-compose.yml  Local PostgreSQL and Redis
```

### 1. Configure Local Environment

Create a local environment file once:

```cmd
copy .env.example .env
```

Open `.env` and set a private `APP_JWT_SECRET` of at least 32 random characters. Configure `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, and `GITHUB_CLIENT_SECRET` for OAuth login. Add `OPENAI_API_KEY` and `SERPER_API_KEY` only when those integrations are enabled. Never commit `.env` or expose provider/API secrets to the frontend.

Register these exact local OAuth callback URLs with the providers:

```text
Google: http://localhost:8090/login/oauth2/code/google
GitHub: http://localhost:8090/login/oauth2/code/github
Frontend callback: http://localhost:5173/auth/callback
```

Use the matching HTTPS URLs for a deployed environment.

### 2. Start Infrastructure

From the project root:

```powershell
docker compose up -d
```

This starts:

| Service | Container | Local Port |
| --- | --- | --- |
| PostgreSQL | ai-support-postgres | 5433 |
| Redis | ai-support-redis | 6380 |

### 3. Apply Database Scripts

SQL scripts are stored in:

```text
docs/sql
```

For a new empty database, apply SQL files in numeric order. For an existing database, apply only migrations that have not already been run; never clear the database or replay migrations against existing data.

```text
000_create_users_table.sql
001_create_tickets_table.sql
002_create_knowledge_articles_table.sql
003_create_ai_question_logs_table.sql
004_add_feedback_to_ai_question_logs.sql
005_create_documents_tables.sql
006_add_document_source_to_ai_question_logs.sql
...
018_create_knowledge_steward_tasks.sql
```

Also apply `019_add_user_session_version.sql` and `020_create_user_oauth_identities.sql` when they are not already present. Back up the database first and never replay scripts blindly against an existing database.

### 4. Start Backend

```powershell
cd ai-support-server
mvn spring-boot:run
```

The local Spring profile loads the project-root `.env` file. Keep this terminal's working directory at `ai-support-server` so that configuration is found.

The backend runs on:

```text
http://localhost:8090
```

Health check:

```text
GET /api/health
```

### 5. Start Frontend

The frontend is stored separately from the backend:

```powershell
cd ai-support-web
npm.cmd install
npm.cmd run dev
```

The frontend runs on:

```text
http://localhost:5173
```

During local development, Vite proxies `/api` requests to:

```text
http://localhost:8090
```

The OAuth authorization and callback paths are proxied to the backend as well.

## UI Direction References

The Stage 3 frontend should follow the reference images in:

```text
Pictures/admin-knowledge-operations-center.png
Pictures/user-document-intelligence-workspace.png
```

These images define the intended CloudOps product style: premium, practical, evidence-centered, and clearly separated between user document Q&A and administrator knowledge operations.

## Main API Overview

### User APIs

```text
GET  /api/auth/oauth/providers
POST /api/auth/oauth/exchange
POST /api/users/logout
GET  /api/users/me
```

### Ticket APIs

```text
POST  /api/tickets
GET   /api/tickets/my
GET   /api/tickets/assigned-to-me
GET   /api/tickets/{id}
GET   /api/tickets
PATCH /api/tickets/{id}/status
PATCH /api/tickets/{id}/assign
PATCH /api/tickets/{id}/resolve
PATCH /api/tickets/{id}/close
```

### Knowledge Base APIs

```text
POST  /api/knowledge/articles
GET   /api/knowledge/articles
GET   /api/knowledge/articles/{id}
GET   /api/knowledge/articles/search?keyword=...
GET   /api/knowledge/articles/categories/stats
PUT   /api/knowledge/articles/{id}
PATCH /api/knowledge/articles/{id}/archive
```

### Document APIs

```text
POST  /api/documents/upload
GET   /api/documents
GET   /api/documents/{documentId}/chunks
GET   /api/documents/chunks/search?keyword=...
GET   /api/documents/categories/stats
PATCH /api/documents/{documentId}/archive
```

### AI Assistant APIs

```text
POST  /api/ai/ask
POST  /api/ai/ask-and-create-ticket
GET   /api/ai/logs/my
GET   /api/ai/logs
PATCH /api/ai/logs/{id}/feedback
GET   /api/ai/stats
GET   /api/ai/knowledge-gaps
POST  /api/ai/knowledge-gaps/{logId}/article
```

### Admin APIs

```text
GET /api/admin/dashboard/overview
```

## Current Architecture

```text
Controller
  -> Service
    -> Mapper
      -> PostgreSQL

Spring Security Filter
  -> JWT validation
  -> Redis token validation
  -> Current user context
```

## Development Workflow

The project follows one focused commit per milestone:

```powershell
git status
git add <changed-files>
git commit -m "feat: describe milestone"
git push
```

Local test JSON and markdown files are stored under `TestFiles/` for manual API testing and should not be committed.

## Roadmap

### Stage 1: Backend Foundation

- Authentication, JWT, Redis login state, and role-aware APIs. MVP complete.
- Ticket workflow and knowledge article management. MVP complete.
- AI-style knowledge answering, logs, feedback, knowledge gaps, and admin dashboard statistics. MVP complete.
- Later: add production-grade tests, richer permissions, paging, filtering, operation logs, and stricter workflow validation.

### Stage 2: Document Knowledge Base

- Add document upload. The current parser supports `.txt`, `.md`, `.pdf`, and `.docx` with resource limits.
- Store document metadata. Done.
- Parse supported text and office formats within bounded resource limits.
- Split documents into searchable chunks. Done.
- Let AI answers reference document sources. Done.
- Next: add focused tests and prepare for frontend product flows.
- Later: add PDF / DOCX parsing, stronger retrieval evaluation, semantic search, confidence scoring, and better document lifecycle controls.

### Stage 3: Frontend Product Experience

- Current focus: build the first usable workspace for login, asking questions, and reviewing document / article source candidates.
- Build a calm, professional user workspace for document-based support.
- Build an admin knowledge operations console.
- Keep the visual style restrained and premium rather than overly obvious or decorative AI styling.

### Stage 4: Retrieval Quality

- Current retrieval is PostgreSQL full-text plus lexical/coverage heuristics.
- Embeddings and vector reranking remain unimplemented and require an evaluated migration plan.
- Continue evaluating route selection, evidence coverage, and citation grounding.

### Stage 5: Web-Augmented Support

- Continue hardening the existing web-search fallback and provider reliability.
- Record external search evidence.
- Let administrators review and save useful findings into the internal knowledge base.

### Stage 6: Quality And Operations

- Add higher-level analytics for knowledge coverage, answer quality, and support automation.
- Add role management and richer permission controls.
- Add automated tests for core services and APIs.
