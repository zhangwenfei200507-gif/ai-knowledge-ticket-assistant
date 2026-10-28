package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.service.KnowledgeStewardService;
import com.zwf.aisupport.vo.KnowledgeArticleVO;
import com.zwf.aisupport.vo.KnowledgeStewardRunVO;
import com.zwf.aisupport.vo.KnowledgeStewardTaskVO;
import com.zwf.aisupport.vo.PageVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/knowledge-steward")
public class KnowledgeStewardController {

    private final KnowledgeStewardService knowledgeStewardService;

    public KnowledgeStewardController(KnowledgeStewardService knowledgeStewardService) {
        this.knowledgeStewardService = knowledgeStewardService;
    }

    @PostMapping("/run")
    public Result<KnowledgeStewardRunVO> run(@RequestParam(required = false) Integer limit) {
        return Result.success(knowledgeStewardService.run(limit));
    }

    @GetMapping("/tasks")
    public Result<PageVO<KnowledgeStewardTaskVO>> listTasks(@RequestParam(required = false) String keyword,
                                                            @RequestParam(required = false) String status,
                                                            @RequestParam(required = false) String sourceType,
                                                            @RequestParam(required = false) String targetDomain,
                                                            @RequestParam(required = false) Integer page,
                                                            @RequestParam(required = false) Integer size) {
        return Result.success(knowledgeStewardService.listTasks(keyword, status, sourceType, targetDomain, page, size));
    }

    @PutMapping("/tasks/{id}")
    public Result<KnowledgeStewardTaskVO> updateTask(@PathVariable Long id,
                                                     @RequestBody KnowledgeStewardTaskVO updateVO) {
        return Result.success(knowledgeStewardService.updateTask(id, updateVO));
    }

    @PostMapping("/tasks/{id}/approve")
    public Result<KnowledgeArticleVO> approve(@PathVariable Long id) {
        return Result.success(knowledgeStewardService.approve(id));
    }

    @PatchMapping("/tasks/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        knowledgeStewardService.reject(id);
        return Result.success();
    }

    @PatchMapping("/tasks/{id}/archive")
    public Result<Void> archive(@PathVariable Long id) {
        knowledgeStewardService.archive(id);
        return Result.success();
    }
}
