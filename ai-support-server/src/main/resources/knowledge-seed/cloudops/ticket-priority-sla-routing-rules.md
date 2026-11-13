# Ticket Priority, SLA, and Routing Rules

Category: Ticket Rules
Primary keywords: ticket, priority, sla, routing, owner, p1, p2, p3, escalation
Source references:
- https://sre.google/resources/practices-and-processes/incident-management-guide/

Purpose

Use this guide to classify support tickets created by users or by AI escalation.

Priority rules

P1:
Production outage, data loss risk, authentication outage, security-sensitive issue, or broad customer impact.

P2:
Major feature degraded, repeated team-level issue, document upload unavailable for a group, or important workaround required.

P3:
Single-user issue, informational request, minor UI problem, or non-urgent knowledge request.

Routing rules

Auth / JWT issues:
Route to platform authentication owner.

Redis Cache issues:
Route to runtime or platform cache owner.

PostgreSQL issues:
Route to database owner.

Docker issues:
Route to runtime or DevOps owner.

Kubernetes issues:
Route to platform infrastructure owner.

Knowledge gaps:
Route to admin knowledge owner for article creation.

SLA model

P1: immediate response and frequent updates.
P2: same business day response.
P3: normal queue handling.

AI escalation rule

If AI cannot find a reliable internal source for a high-risk question, create or recommend a ticket instead of guessing. The ticket should include the user question, searched categories, and whether source type was NONE.

