# Knowledge Library V2

## Product decision

The administrator experience exposes two knowledge surfaces:

1. Knowledge Library: reviewed, durable Knowledge Packs used as the primary answer source.
2. Source Library: uploaded documents, web evidence, model candidates, and review lineage.

Chunks are an internal retrieval index. They remain queryable and inspectable, but they are not a third library and must not be treated as independent articles.

## Canonical hierarchy

```text
Knowledge Library
  Knowledge Pack
    Domain
    Summary
    Lifecycle status
    Version
    Quality state
    Section
      One coherent topic
      Concepts and constraints
      Configuration or mechanism
      Failure modes
      Verification
      References
    Retrieval Chunks (internal)
      One independently answerable concept
      Section path
      Summary
      Keywords
      Question variants
      Source lineage
      Quality state
```

## What belongs where

### Knowledge Pack

An approved, coherent body of knowledge for one domain such as Docker, Redis Cache, PostgreSQL, Kubernetes, or Auth / JWT. A pack is not a collection of pasted documents and must not contain `Title`, `Purpose`, `Knowledge Candidate`, `Observed Answer`, or review instructions.

### Section

A section explains one stable topic. Recommended sections are `Overview`, `Core Concepts`, `Architecture`, `Configuration`, `Workflow`, `Common Issues`, `Troubleshooting`, `Best Practices`, `FAQ`, and `References`. Only sections that contain real knowledge should be created.

### Retrieval Chunk

A chunk is a search unit, not a smaller article. It must preserve one complete concept, code block, or diagnostic relationship. It should be understandable without the rest of the document and must carry enough metadata for filtering and explanation.

### Source Library

Source Library stores raw or unapproved material: uploaded documents, web evidence, LLM-generated evidence, document candidates, and steward tasks. Source material never becomes trusted knowledge merely because it was retrieved.

## Retrieval metadata to add

The current model has `content`, `section_title`, `section_type`, and `concept_tags`. The next schema version should add:

- `section_path`: stable section hierarchy for parent context.
- `summary`: one-sentence meaning of the chunk.
- `keywords`: exact technical terms, error codes, configuration keys, and mechanisms.
- `question_variants`: questions this chunk can answer.
- `source_type` and `source_id`: article, document, web evidence, or model evidence lineage.
- `quality_status`: `DRAFT`, `REVIEWED`, `PUBLISHED`, `ARCHIVED`.
- `semantic_hash`: duplicate detection for equivalent knowledge.

These fields support the enterprise RAG pattern of combining lexical search, semantic retrieval, metadata filtering, and reranking. Microsoft documents the same principle: enrich chunks with cleaned content and metadata, then use metadata as filters or ranking inputs. See [RAG chunk enrichment](https://learn.microsoft.com/en-us/azure/architecture/ai-ml/guide/rag/rag-enrichment-phase) and [hybrid retrieval](https://learn.microsoft.com/en-us/azure/search/retrieval-augmented-generation-overview).

## Lifecycle

```text
Source material
  -> extraction and cleaning
  -> classification
  -> match an existing Knowledge Pack
  -> steward proposal
  -> administrator review
  -> reorganize the pack
  -> regenerate affected chunks
  -> publish and retrieve
```

The system must update an existing pack when the domain and concept match. It may create a new pack only when no existing domain and section can contain the knowledge without distortion.

## Quality rules

- Internal Knowledge Packs are the primary source.
- Category/domain is a hard filter before matching.
- Relevant is not the same as sufficient coverage.
- Raw source text is never the final user answer.
- Web and LLM evidence remain reviewable until an administrator approves the reorganization.
- Rebuilding chunks must replace affected active chunks instead of accumulating duplicates.
- Pagination belongs to operational lists; a long article is navigated by sections, not by making every chunk a page.
