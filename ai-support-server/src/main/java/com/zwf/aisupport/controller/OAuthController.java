package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.config.OAuthProperties;
import com.zwf.aisupport.dto.OAuthExchangeDTO;
import com.zwf.aisupport.service.OAuthAuthenticationService;
import com.zwf.aisupport.vo.LoginVO;
import com.zwf.aisupport.vo.OAuthProvidersVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/oauth")
public class OAuthController {

    private final OAuthProperties properties;
    private final OAuthAuthenticationService authenticationService;

    public OAuthController(OAuthProperties properties,
                           OAuthAuthenticationService authenticationService) {
        this.properties = properties;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/providers")
    public Result<OAuthProvidersVO> providers() {
        return Result.success(new OAuthProvidersVO(
                properties.getGoogle().isConfigured(),
                properties.getGithub().isConfigured()
        ));
    }

    @PostMapping("/exchange")
    public Result<LoginVO> exchange(@Valid @RequestBody OAuthExchangeDTO request) {
        return Result.success(authenticationService.exchange(request.getCode()));
    }
}
