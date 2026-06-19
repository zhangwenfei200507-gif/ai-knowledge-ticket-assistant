# Ticket Triage and Customer Communication Playbook

Category: CloudOps Support
Primary keywords: ticket, triage, priority, customer communication, support, escalation, p1, p2, p3, owner, sla
Source references:
- https://sre.google/resources/practices-and-processes/incident-management-guide/
- https://sre.google/sre-book/managing-incidents/

Purpose

Use this playbook when an AI answer cannot resolve a user question, a ticket is created from a knowledge gap, or support must decide severity, owner, and user-facing communication.

Ticket fields that must be captured

- User impact: who is affected and what workflow is blocked.
- Symptom: exact error, status code, screenshot, or failed action.
- Scope: single user, team, region, or global.
- Start time: when the issue began or when it was first noticed.
- Recent change: deployment, config update, secret rotation, database migration, or infrastructure change.
- Source evidence: AI answer source, document chunk, article, log excerpt, or command output.
- Owner: team responsible for next action.

Priority model

P1: Production outage, security-sensitive login failure, database write failure, data loss risk, or major customer impact.

P2: Critical workflow degraded for a team or repeated issue with known workaround.

P3: Individual user issue, informational request, low-risk configuration question, or non-urgent knowledge request.

AI-created ticket rule

When the assistant cannot find a reliable source, it should recommend ticket creation. The ticket description should include the original question, why the assistant could not answer, and which categories were searched.

Triage checklist

1. Confirm whether the issue is a known incident.
2. Search internal documents and articles for the same symptom.
3. Check recent deployments or configuration changes.
4. Assign priority using impact and urgency.
5. Route to the owning team.
6. Ask for missing evidence only when it changes the next action.
7. Convert repeated tickets into knowledge articles.

Customer update template

```text
We are investigating an issue affecting <workflow>.
Current impact: <scope>.
What we know: <confirmed facts only>.
Current action: <diagnosis or mitigation>.
Next update: <time>.
Workaround: <if available>.
```

Resolution template

```text
The issue is resolved.
Root cause: <short confirmed cause>.
Fix applied: <action>.
User action needed: <none / sign in again / retry upload / refresh page>.
Follow-up: <knowledge article, monitor, or prevention task>.
```

Escalation triggers

- Multiple users report the same login or upload failure.
- A ticket mentions production, data loss, security, or cannot login.
- AI returns no source for a high-risk operational question.
- The owning team is unclear after initial triage.

Knowledge follow-up

Every ticket closed with a reusable answer should produce an article or document update. The best knowledge item includes symptom, diagnosis, safe checks, fix, rollback, owner, and escalation criteria.

