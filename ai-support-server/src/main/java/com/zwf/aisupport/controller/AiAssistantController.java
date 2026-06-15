package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.vo.AiAnswerVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {

    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/ask")
    public Result<AiAnswerVO> ask(@Valid @RequestBody AiAskDTO aiAskDTO) {
        AiAnswerVO answer = aiAssistantService.ask(aiAskDTO);
        return Result.success(answer);
    }
}