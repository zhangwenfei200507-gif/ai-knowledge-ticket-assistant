# AI Document Knowledge & Web-Augmented Support Platform

An enterprise-style Spring Boot backend project for building an AI-powered document knowledge and support platform.

The long-term product direction is clear: users upload documents and ask questions, the system searches internal documents and knowledge articles first, uses web search as a later-stage fallback when internal knowledge is insufficient, and creates support tickets when the issue still needs human handling.

The current backend focuses on the first stable foundation plus the first document knowledge layer: authentication, ticket workflow, structured knowledge articles, uploaded `.txt` / `.md` documents, document chunk search, AI-style question answering, AI logs, feedback, knowledge gaps, and admin analytics. RAG, semantic retrieval, and web-augmented answering will be added in later stages.

## Product Direction

This project is not a simple ticket system, a simple CRUD knowledge base, or a thin chatbot wrapper.

It is designed around this loop:

```text
Upload documents
  -> Parse and store knowledge
  -> Ask questions
  -> Search internal documents and knowledge articles
  -> Return answers with sources
  -> Use web search when internal knowledge is insufficient
  -> Create tickets when human support is still required
  -> Record knowledge gaps
  -> Improve the knowledge base over time
```

The goal is to make AI the first-response layer for support, while keeping human review, knowledge quality, and traceable sources at the center of the system.

## Project Highlights

- JWT authentication with Redis-backed token validation and logout.
- User registration, login, current-user query, and role-based access control.
- Ticket lifecycle management, including creation, assignment, resolution, and closure.
- Knowledge base article creation, search, update, archive, and category statistics.
- Uploaded document metadata, text chunking, keyword search, archive flow, and category stats.
- AI assistant API that answers from uploaded documents or knowledge articles and creates tickets when needed.
- AI question logging, answer feedback, dashboard statistics, and knowledge-gap discovery.
- Closed-loop knowledge improvement: unanswered AI questions can be converted into reusable knowledge articles.
- Docker Compose based local infrastructure with PostgreSQL and Redis.
- Clear layered backend structure: Controller -> Service -> Mapper -> Database.
- A staged AI roadmap: structured and document knowledge search first, RAG later, and web search as an advanced fallback.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Java 17 |
| Backend Framework | Spring Boot 3.5.15 |
| Build Tool | Maven |
| Database | PostgreSQL |
| Cache / Login State | Redis |
| Persistence | MyBatis |
| Security | Spring Security, JWT |
| Local Infrastructure | Docker Compose |
| Version Control | Git, GitHub |

## Module Overview

### Current Phase

The current implementation uses uploaded document chunks first and structured knowledge articles second. This keeps the foundation stable before adding embeddings, semantic retrieval, and web-augmented answers.

### User And Authentication

- User registration and login.
- BCrypt password encoding.
- JWT token generation.
- Redis-backed active token storage.
- Logout by removing token state from Redis.
- Current user context extracted from authenticated requests.

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

### Future Web-Augmented Answering

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

### 1. Start Infrastructure

From the project root:

```powershell
docker compose up -d
```

This starts:

| Service | Container | Local Port |
| --- | --- | --- |
| PostgreSQL | ai-support-postgres | 5433 |
| Redis | ai-support-redis | 6380 |

### 2. Run SQL Scripts

SQL scripts are stored in:

```text
docs/sql
```

Run them in order when setting up a fresh local database:

```text
001_create_tickets_table.sql
002_create_knowledge_articles_table.sql
003_create_ai_question_logs_table.sql
004_add_feedback_to_ai_question_logs.sql
005_create_documents_tables.sql
006_add_document_source_to_ai_question_logs.sql
```

### 3. Start Backend

```powershell
cd ai-support-server
mvn clean spring-boot:run
```

The backend runs on:

```text
http://localhost:8090
```

Health check:

```text
GET /api/health
```

## Main API Overview

### User APIs

```text
POST /api/users/register
POST /api/users/login
POST /api/users/logout
GET  /api/users/me
GET  /api/users/{username}
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

Local test JSON files such as `ask.json`, `article.json`, `gap-article.json`, and `article-update.json` are only used for manual API testing and should not be committed.

## Roadmap

### Stage 1: Backend Foundation

- Authentication, JWT, Redis login state, and role-aware APIs.
- Ticket workflow and knowledge article management.
- AI-style knowledge answering, logs, feedback, knowledge gaps, and admin dashboard statistics.

### Stage 2: Document Knowledge Base

- Add document upload. Done for `.txt` and `.md`.
- Store document metadata. Done.
- Parse `.txt` and `.md` first, then expand to `.pdf` and `.docx`. Current parser supports `.txt` and `.md`.
- Split documents into searchable chunks. Done.
- Let AI answers reference document sources. Done.
- Next: add focused tests and prepare for frontend product flows.

### Stage 3: Frontend Product Experience

- Build a calm, professional user workspace for document-based support.
- Build an admin knowledge operations console.
- Keep the visual style restrained and premium rather than overly obvious or decorative AI styling.

### Stage 4: RAG And Semantic Retrieval

- Add embeddings.
- Add vector-based semantic search.
- Support multi-source answer generation.
- Add confidence and source ranking.

### Stage 5: Web-Augmented Support

- Add web search fallback for questions not covered by internal knowledge.
- Record external search evidence.
- Let administrators review and save useful findings into the internal knowledge base.

### Stage 6: Quality And Operations

- Add higher-level analytics for knowledge coverage, answer quality, and support automation.
- Add role management and richer permission controls.
- Add automated tests for core services and APIs.
