package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.service.LlmGatewayService;
import com.zwf.aisupport.vo.LlmGatewayStatusVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai/llm")
public class LlmGatewayController {

    private final LlmGatewayService llmGatewayService;

    public LlmGatewayController(LlmGatewayService llmGatewayService) {
        this.llmGatewayService = llmGatewayService;
    }

    @GetMapping("/status")
    public Result<LlmGatewayStatusVO> getStatus() {
        CurrentUserUtil.requireAdmin("Only administrators can inspect the LLM gateway");
        return Result.success(llmGatewayService.getStatus());
    }
}
