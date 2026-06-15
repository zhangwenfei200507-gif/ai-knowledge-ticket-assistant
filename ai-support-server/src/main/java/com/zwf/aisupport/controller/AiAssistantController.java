package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.vo.AiAnswerVO;
import com.zwf.aisupport.vo.AiQuestionLogVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/ask-and-create-ticket")
    public Result<AiAnswerVO> askAndCreateTicket(@Valid @RequestBody AiAskDTO aiAskDTO) {
        AiAnswerVO answer = aiAssistantService.askAndCreateTicket(aiAskDTO);
        return Result.success(answer);
    }
    @GetMapping("/logs/my")
    public Result<List<AiQuestionLogVO>> listMyQuestionLogs() {
        List<AiQuestionLogVO> logs = aiAssistantService.listMyQuestionLogs();
        return Result.success(logs);
    }

    @GetMapping("/logs")
    public Result<List<AiQuestionLogVO>> listAllQuestionLogs() {
        List<AiQuestionLogVO> logs = aiAssistantService.listAllQuestionLogs();
        return Result.success(logs);
    }
}