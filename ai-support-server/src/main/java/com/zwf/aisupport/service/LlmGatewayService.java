package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.AiAnswerSourceVO;
import com.zwf.aisupport.vo.LlmGatewayStatusVO;
import com.zwf.aisupport.service.model.LlmErrorCategory;
import com.zwf.aisupport.service.model.LlmGenerationResult;

import java.util.List;
import java.util.Optional;

public interface LlmGatewayService {

    Optional<String> generateGroundedAnswer(String question, String sourceType, List<AiAnswerSourceVO> sources);

    Optional<String> generateDirectAnswer(String question);

    Optional<String> generateKnowledgeStewardProposal(String taskInput);

    default LlmGenerationResult generateGroundedAnswerResult(String question,
                                                              String sourceType,
                                                              List<AiAnswerSourceVO> sources) {
        Optional<String> answer = generateGroundedAnswer(question, sourceType, sources);
        String value = answer == null ? null : answer.orElse(null);
        return new LlmGenerationResult(
                value, null, null, null, null, null, null, null, true,
                0, 0, value == null ? "FAILED" : "SUCCEEDED",
                value == null ? LlmErrorCategory.LLM_NO_USABLE_RESPONSE : null,
                value == null ? "No LLM result" : null
        );
    }

    default LlmGenerationResult generateDirectAnswerResult(String question) {
        Optional<String> answer = generateDirectAnswer(question);
        String value = answer == null ? null : answer.orElse(null);
        return new LlmGenerationResult(
                value, null, null, null, null, null, null, null, true,
                0, 0, value == null ? "FAILED" : "SUCCEEDED",
                value == null ? LlmErrorCategory.LLM_NO_USABLE_RESPONSE : null,
                value == null ? "No LLM result" : null
        );
    }

    LlmGatewayStatusVO getStatus();

    boolean isConfigured();
}
