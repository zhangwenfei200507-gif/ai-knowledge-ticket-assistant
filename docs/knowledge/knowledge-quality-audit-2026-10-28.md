# AegisCloud Knowledge Quality Audit Plan

Status: Static audit definition only. No database scan or mutation is performed by this document.

## Purpose

This audit keeps the published Knowledge Library small, domain-consistent, source-backed, and suitable for section-level retrieval. It is intentionally read-only. Any repair must produce a reviewable plan and preserve article versions, source links, document relationships, and rejected history.

## Audit dimensions

| Area | Checks | Safe outcome |
| --- | --- | --- |
| Knowledge Pack | Empty content, duplicated titles, duplicate concepts, mixed domains, template residue | Report a repair candidate; never auto-publish |
| Sections | Missing heading hierarchy, repeated concepts, procedure text mixed with definitions | Recommend section-level rewrite |
| Chunks | Empty content, broken code block, sentence truncation, duplicate semantic signature, missing article link | Mark for semantic rebuild |
| Source documents | Parse/index failures, unsupported type, duplicate checksum, missing metadata | Keep source record and create reprocessing task |
| Review queue | Missing evidence, stale status, duplicate candidate, invalid category | Keep history and route to review |
| Web evidence | Invalid URL, low-quality page, duplicate URL, missing retrieval metadata | Reject or archive with reason; keep audit history |
| Retrieval | Orphaned chunk, missing embedding/index state, cross-domain match | Exclude from answer and create repair item |

## Repair policy

1. Domain is a hard boundary. A candidate cannot update a Knowledge Pack in another domain.
2. A related article is not automatically sufficient evidence. Coverage must include the question's required concepts.
3. Reorganization rewrites the affected section and then rebuilds its semantic chunks; it does not append a `Knowledge Candidate` section.
4. Unverified facts remain pending review. They are never silently promoted to published knowledge.
5. Deletion is a lifecycle operation. Use archive or soft-delete with an audit event when relationships exist.
6. Existing article history and original evidence remain recoverable after a merge or rewrite.

## Reviewable repair output

Every repair batch should produce:

- affected record IDs;
- detected issue and severity;
- proposed target Knowledge Pack and section;
- source evidence IDs;
- proposed content diff;
- chunk rebuild scope;
- reviewer and decision fields;
- post-repair retrieval questions.

The output is a plan until an administrator approves it.

