# AI-Powered Knowledge Base & Ticket Assistant

An enterprise-style backend project for learning modern Java development through practice.

The system will grow into an AI knowledge base and ticket assistant that supports document upload, RAG-based question answering, ticket workflow, JWT authentication, Redis caching, database persistence, and Docker-based local development.

## Learning Goals

- Build a clear Spring Boot backend structure.
- Practice real Git workflow with one focused commit per milestone.
- Use PostgreSQL or MySQL for business data.
- Use Redis for caching, rate limiting, and temporary task state.
- Use JWT for authentication and authorization.
- Add AI-powered document Q&A with retrieval-augmented generation.
- Package the project with Docker Compose for local development.

## Current Environment Check

Checked on 2026-06-11:

| Tool | Status |
| --- | --- |
| Git | Available |
| Java | Not found in command environment |
| Maven | Not found in command environment |
| Docker | Not found in command environment |
| GitHub CLI | Not found in command environment |

Before running the backend, install or configure:

- JDK 17
- Maven 3.9+
- Docker Desktop
- GitHub CLI, or use a GitHub remote URL manually

## Local Services

After Docker is installed, copy `.env.example` to `.env`, edit local passwords, then start services:

```powershell
docker compose up -d
```

This starts:

- PostgreSQL on port `5432`
- Redis on port `6379`

## Git Workflow

Each learning milestone should be committed separately:

```powershell
git status
git add .
git commit -m "chore: initialize project environment"
```

GitHub push will be configured after a remote repository URL is available.

