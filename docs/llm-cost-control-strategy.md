# LLM Cost Control Strategy

This project should not call a large model for every user question.

The large model is a premium reasoning layer. Internal Knowledge Packs, Article Chunks, Document Chunks, and Web Evidence remain the primary system of record.

## Routing Order

```text
User question
-> Question contract
-> Article Chunk retrieval
-> Coverage check
-> If sufficient: local grounded answer
-> If partial: Web Search
-> Web coverage check
-> If sufficient: local grounded answer from internal + web
-> If answer quality fails: LLM grounded synthesis
-> If no reliable internal/web source: LLM fallback
-> Store model result as review evidence
```

## When LLM Is Allowed

LLM calls are allowed only when at least one of these conditions is true:

- Internal knowledge is relevant but the final local answer fails quality checks.
- Internal knowledge partially covers the question and Web Evidence is needed to complete it.
- Web Search finds reliable evidence but local synthesis would copy raw source text or produce a weak answer.
- Internal and Web Search both fail, but the question is within the project domain and a careful fallback answer is valuable.
- Administrator approves a Knowledge Pack reorganization where the current article needs semantic rewriting, not simple appending.

## When LLM Is Not Allowed

LLM calls should not be used when:

- Article Chunks fully answer the question and the local answer passes quality checks.
- The question is clearly outside CloudOps / platform / knowledge-base scope.
- The question asks for private production facts that are not in internal sources.
- The request is a simple CRUD/admin action.
- The answer would expose secrets, credentials, tokens, private user data, or internal-only production identifiers.

## Cost Controls

- Use the smallest model suitable for grounded synthesis.
- Send only selected Article Chunks or Web Evidence, not full articles.
- Limit evidence context to the top 3 to 5 chunks.
- Cache reviewed answers through Knowledge Pack updates.
- Store useful LLM outputs as Review Evidence so the same topic does not repeatedly spend tokens.
- Prefer article reorganization after repeated misses instead of repeated direct LLM answers.

## Local Configuration

The backend reads OpenAI settings from environment variables. Never commit a real API key.

```text
OPENAI_API_KEY=<secret>
OPENAI_MODEL=gpt-5-nano
OPENAI_MAX_EVIDENCE_CHARS=4800
OPENAI_MAX_SOURCE_CHARS=1200
OPENAI_MAX_OUTPUT_TOKENS=700
AI_GROUNDED_SYNTHESIS_ENABLED=true
AI_DIRECT_FALLBACK_ENABLED=true
AI_MAX_GROUNDED_SOURCES=4
AI_MAX_PREVIEW_CHARS_PER_SOURCE=1200
AI_MAX_DIRECT_FALLBACK_QUESTION_CHARS=800
```

Recommended local default:

- Use `gpt-5-nano` for low-cost answer synthesis and evidence cleanup.
- Keep direct fallback enabled only for CloudOps-style questions.
- Lower `AI_MAX_GROUNDED_SOURCES` or `OPENAI_MAX_EVIDENCE_CHARS` if token usage grows too fast.

## Quality Controls

Before returning an LLM answer:

- Check that it answers the user question directly.
- Check that it does not copy raw source text.
- Check that it does not expose internal article titles, chunk names, runbook labels, or review workflow.
- Check that it cites the grounding source type without replacing the answer with grounding.
- If confidence is low, create review evidence or a ticket instead of pretending certainty.

## Knowledge Learning Rule

Whenever LLM is used for a topic that belongs to the project domain:

- Save the answer as `LLM_GENERATED` evidence.
- Put it in `NEEDS_REVIEW`.
- Do not publish directly.
- When approved, reorganize it into the closest Knowledge Pack section.
- Regenerate Article Chunks for the affected Knowledge Pack.

This keeps token usage low over time because repeated topics become internal knowledge.
