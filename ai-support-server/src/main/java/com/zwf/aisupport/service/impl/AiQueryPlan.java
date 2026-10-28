package com.zwf.aisupport.service.impl;

import java.util.List;

public record AiQueryPlan(String originalQuestion,
                          List<String> tasks,
                          List<String> requiredConcepts,
                          List<String> anchorConcepts,
                          List<String> expectedOutputs,
                          List<String> retrievalTerms,
                          List<String> domainCandidates,
                          boolean externalEvidenceRequired,
                          AiAnswerIntent intent) {
}
