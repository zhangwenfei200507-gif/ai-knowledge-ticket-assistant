package com.zwf.aisupport.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.routing")
public class AiRoutingProperties {

    private boolean groundedSynthesisEnabled = true;
    private boolean directFallbackEnabled = true;
    private String groundedSynthesisSourceTypes = "ARTICLE,DOCUMENT,WEB";
    private int maxGroundedSources = 4;
    private int maxPreviewCharsPerSource = 1200;
    private int maxQuestionCharsForDirectFallback = 800;

    public boolean isGroundedSynthesisEnabled() {
        return groundedSynthesisEnabled;
    }

    public void setGroundedSynthesisEnabled(boolean groundedSynthesisEnabled) {
        this.groundedSynthesisEnabled = groundedSynthesisEnabled;
    }

    public boolean isDirectFallbackEnabled() {
        return directFallbackEnabled;
    }

    public void setDirectFallbackEnabled(boolean directFallbackEnabled) {
        this.directFallbackEnabled = directFallbackEnabled;
    }

    public String getGroundedSynthesisSourceTypes() {
        return groundedSynthesisSourceTypes;
    }

    public void setGroundedSynthesisSourceTypes(String groundedSynthesisSourceTypes) {
        this.groundedSynthesisSourceTypes = groundedSynthesisSourceTypes == null || groundedSynthesisSourceTypes.isBlank()
                ? "ARTICLE,DOCUMENT,WEB"
                : groundedSynthesisSourceTypes;
    }

    public int getMaxGroundedSources() {
        return maxGroundedSources;
    }

    public void setMaxGroundedSources(int maxGroundedSources) {
        this.maxGroundedSources = Math.max(1, maxGroundedSources);
    }

    public int getMaxPreviewCharsPerSource() {
        return maxPreviewCharsPerSource;
    }

    public void setMaxPreviewCharsPerSource(int maxPreviewCharsPerSource) {
        this.maxPreviewCharsPerSource = Math.max(300, maxPreviewCharsPerSource);
    }

    public int getMaxQuestionCharsForDirectFallback() {
        return maxQuestionCharsForDirectFallback;
    }

    public void setMaxQuestionCharsForDirectFallback(int maxQuestionCharsForDirectFallback) {
        this.maxQuestionCharsForDirectFallback = Math.max(120, maxQuestionCharsForDirectFallback);
    }
}
