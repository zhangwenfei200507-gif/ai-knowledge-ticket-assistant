package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.dto.AiAskDTO;
import com.zwf.aisupport.dto.AiQuestionLogFeedbackDTO;
import com.zwf.aisupport.dto.KnowledgeArticleCreateDTO;
import com.zwf.aisupport.service.AiAssistantService;
import com.zwf.aisupport.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/ask-with-file")
    public Result<AiAnswerVO> askWithFile(@RequestParam("question") String question,
                                          @RequestParam(value = "file", required = false) MultipartFile file) {
        AiAnswerVO answer = aiAssistantService.askWithFile(question, file);
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
    public Result<PageVO<AiQuestionLogVO>> listAllQuestionLogs(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) String sourceType,
                                                               @RequestParam(required = false) String feedback,
                                                               @RequestParam(required = false) Integer page,
                                                               @RequestParam(required = false) Integer size) {
        return Result.success(aiAssistantService.listAllQuestionLogs(keyword, sourceType, feedback, page, size));
    }

    @DeleteMapping("/logs/{id}")
    public Result<Void> deleteQuestionLog(@PathVariable Long id) {
        aiAssistantService.deleteQuestionLog(id);
        return Result.success();
    }

    @PatchMapping("/logs/{id}/feedback")
    public Result<Void> submitQuestionLogFeedback(@PathVariable Long id,
                                                  @Valid @RequestBody AiQuestionLogFeedbackDTO feedbackDTO) {
        aiAssistantService.submitQuestionLogFeedback(id, feedbackDTO);
        return Result.success();
    }
    @GetMapping("/stats")
    public Result<AiDashboardStatsVO> getDashboardStats() {
        AiDashboardStatsVO stats = aiAssistantService.getDashboardStats();
        return Result.success(stats);
    }
    @GetMapping("/knowledge-gaps")
    public Result<PageVO<KnowledgeGapVO>> listKnowledgeGaps(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return Result.success(aiAssistantService.listKnowledgeGaps(keyword, category, status, page, size));
    }
    @PostMapping("/knowledge-gaps/{logId}/article")
    public Result<KnowledgeArticleVO> createArticleFromKnowledgeGap(@PathVariable Long logId,
                                                                    @Valid @RequestBody KnowledgeArticleCreateDTO createDTO) {
        KnowledgeArticleVO article = aiAssistantService.createArticleFromKnowledgeGap(logId, createDTO);
        return Result.success(article);
    }

    @DeleteMapping("/knowledge-gaps/{logId}")
    public Result<Void> deleteKnowledgeGap(@PathVariable Long logId) {
        aiAssistantService.deleteKnowledgeGap(logId);
        return Result.success();
    }
}
