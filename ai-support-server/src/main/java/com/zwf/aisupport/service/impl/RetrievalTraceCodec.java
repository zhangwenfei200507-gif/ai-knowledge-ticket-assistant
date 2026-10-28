package com.zwf.aisupport.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zwf.aisupport.vo.AiRetrievalTraceVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetrievalTraceCodec {

    private static final Logger log = LoggerFactory.getLogger(RetrievalTraceCodec.class);

    private final ObjectMapper objectMapper;

    public RetrievalTraceCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String encode(AiRetrievalTraceVO trace) {
        if (trace == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(trace);
        } catch (JsonProcessingException exception) {
            log.warn("Retrieval trace serialization failed: {}", exception.getOriginalMessage());
            return null;
        }
    }

    public AiRetrievalTraceVO decode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, AiRetrievalTraceVO.class);
        } catch (JsonProcessingException exception) {
            log.warn("Stored retrieval trace could not be read: {}", exception.getOriginalMessage());
            return null;
        }
    }
}
