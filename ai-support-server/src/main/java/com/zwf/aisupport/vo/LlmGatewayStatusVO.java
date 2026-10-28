package com.zwf.aisupport.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LlmGatewayStatusVO {

    private boolean configured;
    private String provider;
    private String model;
    private String baseUrl;
    private long totalCalls;
    private long successfulCalls;
    private long failedCalls;
    private String lastCallType;
    private String lastFailureReason;
    private LocalDateTime lastCallTime;
}
