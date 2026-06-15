package com.zwf.aisupport.service;

import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.vo.AiAnswerVO;

public interface AiAssistantService {

    AiAnswerVO ask(AiAskDTO aiAskDTO);

    AiAnswerVO askAndCreateTicket(AiAskDTO aiAskDTO);
}