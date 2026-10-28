# Retrieval Evaluation Plan

Status: Evaluation design only. Metrics are not populated until an authorized test run supplies labeled data.

## Retrieval contract

`Query analysis -> domain filter -> lexical retrieval -> semantic retrieval (when configured) -> rank fusion -> coverage evaluation -> answer composition`.

The system must keep the internal source as the primary evidence. Web evidence can supplement a partial internal result, but it cannot silently replace a directly relevant internal fact.

## Required labels

Each evaluation question should record:

- expected domain;
- expected article and section IDs;
- required concepts;
- optional concepts;
- acceptable answer facts;
- forbidden facts or cross-domain sources;
- expected route: INTERNAL_RAG, WEB_SEARCH, AI_ASSISTED_WEB, or AI_ONLY.

## Metrics

- Recall@5 and Recall@10 for labeled evidence;
- MRR and nDCG@10 for ranking;
- concept coverage rate;
- citation precision;
- grounded answer rate;
- unsupported claim rate;
- web fallback precision;
- average retrieval latency and p95 retrieval latency.

## Failure interpretation

- Low recall means the index or query representation is incomplete.
- Good recall with low coverage means the wrong chunks are being selected or the article is too broad.
- Good coverage with unsupported claims means answer composition is violating the evidence boundary.
- Correct internal evidence followed by unnecessary web search means routing policy is too eager.
- Web fallback without a source means the provider or evidence quality gate failed.

No metric should be reported as a pass without a labeled test set and an actual run record.

