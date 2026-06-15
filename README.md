# AI-Powered Knowledge Base & Ticket Assistant

An enterprise-style Spring Boot backend project that combines user authentication, ticket workflow, knowledge base management, Redis-backed login state, and AI-assisted support automation.

The project is designed as a learning-by-building system, but its product direction follows a real AI support platform: users ask questions, the system searches the knowledge base, creates tickets when no reliable answer is found, records AI usage, collects feedback, identifies knowledge gaps, and helps administrators improve the knowledge base over time.

## Project Highlights

- JWT authentication with Redis-backed token validation and logout.
- User registration, login, current-user query, and role-based access control.
- Ticket lifecycle management, including creation, assignment, resolution, and closure.
- Knowledge base article creation, search, update, archive, and category statistics.
- AI assistant API that answers from the knowledge base and creates tickets when needed.
- AI question logging, answer feedback, dashboard statistics, and knowledge-gap discovery.
- Closed-loop knowledge improvement: unanswered AI questions can be converted into reusable knowledge articles.
- Docker Compose based local infrastructure with PostgreSQL and Redis.
- Clear layered backend structure: Controller -> Service -> Mapper -> Database.

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

### AI Assistant

- Ask AI based on the current knowledge base.
- Return source article information when an answer is found.
- Suggest ticket creation when no reliable answer exists.
- Automatically create tickets from unanswered questions.
- Generate AI-created ticket titles and priority.
- Record AI question logs.
- Collect feedback on AI answers.
- Report AI dashboard statistics.
- Identify knowledge gaps.
- Convert knowledge gaps into new knowledge articles.

### Admin Dashboard

- Total ticket count.
- Open and in-progress ticket count.
- Resolved and closed ticket count.
- Published knowledge article count.
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

- Add a Vue-based web frontend for users and administrators.
- Add document upload and parsing for knowledge ingestion.
- Add vector search and RAG-based semantic retrieval.
- Add external web search fallback for questions not covered by the local knowledge base.
- Add AI-generated draft answers for support tickets.
- Add role management and richer permission controls.
- Add automated tests for core services and APIs.
