# Incident Postmortem and Knowledge Update Template

Category: Incident SOP
Primary keywords: incident, postmortem, timeline, root cause, corrective action, knowledge update, runbook
Source references:
- https://sre.google/sre-book/postmortem-culture/
- https://sre.google/workbook/incident-response/

Purpose

Use this template after an incident is resolved. The goal is not blame. The goal is to convert operational learning into better detection, better runbooks, and better AI knowledge.

Postmortem sections

Incident summary:
What happened, which users were affected, and what workflow failed.

Impact:
Duration, affected systems, customer-visible symptoms, and severity.

Timeline:
Detection time, first response, mitigation, recovery, and final resolution.

Root cause:
The confirmed technical cause, not the first symptom.

Contributing factors:
Missing alerts, unclear ownership, weak runbooks, insufficient tests, or risky deployment process.

What went well:
Fast detection, useful dashboard, clear owner, successful rollback, or good customer communication.

What went poorly:
Slow triage, missing logs, stale documentation, confusing alerts, or manual recovery steps.

Corrective actions:
Each action should have an owner, due date, and measurable outcome.

Knowledge update:
Create or update internal articles and documents so the same issue is easier to answer next time.

AI knowledge requirements

- Add exact symptoms and error messages.
- Add safe diagnostic steps.
- Add recovery actions.
- Add escalation criteria.
- Add links to source references.
- Archive outdated content that could mislead the assistant.

