# CloudOps Incident Response and Escalation SOP

Category: Incident SOP
Primary keywords: incident, outage, escalation, priority, p1, p2, postmortem, commander, communication, ticket
Source references:
- https://sre.google/resources/practices-and-processes/incident-management-guide/
- https://sre.google/workbook/incident-response/
- https://sre.google/sre-book/managing-incidents/

Purpose

Use this SOP when a user issue may represent a service incident, repeated support tickets point to the same failure, or the AI cannot answer a high-impact production question from existing knowledge.

Severity model

P1: Broad production outage, security-sensitive authentication failure, data loss risk, or core user workflow unavailable.

P2: Major degradation affecting a group of users, repeated failures in one critical component, or workaround exists but is painful.

P3: Limited impact, single-user issue, non-critical feature degradation, or informational support request.

Incident roles

Incident commander: owns coordination, decisions, and timeline.

Technical lead: owns diagnosis and mitigation.

Communications owner: posts updates to users or stakeholders.

Scribe: records timeline, commands, observations, and decisions.

Fast triage checklist

1. Confirm impact: who is affected, what workflow fails, when it started.
2. Check whether there was a recent deployment, config change, secret rotation, or infrastructure change.
3. Assign severity.
4. Create or link a ticket.
5. Identify the owning team.
6. Start a timeline.
7. Mitigate before root-cause perfection when impact is high.

Escalation triggers

- Login or authorization failure affects multiple users.
- Document upload fails for all users.
- Database writes fail.
- Redis outage affects session or token behavior.
- Kubernetes deployment causes repeated CrashLoopBackOff.
- AI answers become unreliable because knowledge retrieval fails.

Communication template

```text
Status: Investigating / Mitigating / Monitoring / Resolved
Impact: <who and what workflow>
Start time: <known or estimated>
Current action: <what the team is checking or changing>
Next update: <time>
Workaround: <if available>
```

Post-incident review

Capture timeline, detection source, customer impact, root cause, contributing factors, mitigation, prevention tasks, and knowledge articles that should be created or updated.

Knowledge follow-up

Every resolved incident should update the internal knowledge base. Add or revise runbooks for symptoms, commands, owner team, escalation criteria, and verified fixes. If users asked the same question repeatedly, create an article from the knowledge gap.

Prevention controls

- Alert on symptoms users feel, not only internal causes.
- Keep runbooks near the systems they describe.
- Track unanswered AI questions as knowledge gaps.
- Keep ownership clear for authentication, database, cache, container runtime, Kubernetes, and AI retrieval.
