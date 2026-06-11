# Learning Roadmap

## Phase 0: Environment and Repository

- Confirm Java, Maven, Git, Docker, and GitHub access.
- Create project repository.
- Add local service definitions for PostgreSQL and Redis.
- Keep secrets out of Git with `.env`.

## Phase 1: Backend Foundation

- Create a Spring Boot multi-module project.
- Add common response objects and global exception handling.
- Add basic health check APIs.
- Configure profiles for local development.

## Phase 2: Authentication and Users

- Create user and role tables.
- Implement registration and login.
- Add JWT authentication.
- Add Redis-backed token/session support where useful.

## Phase 3: Ticket Workflow

- Create ticket CRUD APIs.
- Add ticket status transitions.
- Add pagination, filtering, and operation logs.
- Add transaction handling and validation.

## Phase 4: Knowledge Base

- Upload and manage documents.
- Parse text content.
- Split documents into searchable chunks.
- Store metadata and chunk records.

## Phase 5: AI Q&A

- Generate embeddings for document chunks.
- Add semantic retrieval.
- Build RAG answers with source citations.
- Stream AI responses with SSE or WebSocket.

## Phase 6: Engineering Polish

- Add tests.
- Add logging and request tracing.
- Add rate limiting.
- Improve Docker setup.
- Write a resume-ready README.

