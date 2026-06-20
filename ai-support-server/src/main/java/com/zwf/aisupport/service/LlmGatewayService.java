package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.AiAnswerSourceVO;

import java.util.List;
import java.util.Optional;

public interface LlmGatewayService {

    Optional<String> generateGroundedAnswer(String question, String sourceType, List<AiAnswerSourceVO> sources);

    Optional<String> generateDirectAnswer(String question);

    boolean isConfigured();
}
