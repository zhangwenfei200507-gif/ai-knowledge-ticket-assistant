# Customer Status Update Templates

Category: Customer Templates
Primary keywords: customer update, communication, incident message, resolved message, workaround, support response
Source references:
- https://sre.google/sre-book/managing-incidents/

Purpose

Use these templates when support needs to communicate clearly during an outage, degraded service, or resolved issue.

Investigating template

```text
Status: Investigating
Impact: <workflow and affected users>
What we know: <confirmed facts only>
Current action: <what the team is checking>
Next update: <time>
Workaround: <if available>
```

Mitigating template

```text
Status: Mitigating
Impact: <current user impact>
Action in progress: <rollback, restart, config fix, capacity change>
Expected user action: <none, retry, sign in again>
Next update: <time>
```

Resolved template

```text
Status: Resolved
Root cause: <short confirmed cause>
Fix applied: <what changed>
User action: <none, refresh page, sign in again, retry upload>
Follow-up: <monitoring, runbook update, postmortem>
```

Support response principles

- Do not speculate.
- Separate symptoms from confirmed root cause.
- Give next update time during active incidents.
- Mention workaround only when verified.
- After resolution, update the knowledge base with the reusable answer.

