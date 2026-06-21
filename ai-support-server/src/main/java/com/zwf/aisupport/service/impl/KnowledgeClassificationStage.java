package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.KnowledgeCategoryResolver;
import com.zwf.aisupport.common.util.TextUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeClassificationStage {

    public KnowledgeClassification classify(String fallbackDomain, String title, String content) {
        String combined = title + " " + content;
        String domain = KnowledgeCategoryResolver.infer(fallbackDomain, combined);
        return new KnowledgeClassification(domain, inferType(combined), extractConcepts(combined));
    }

    private String inferType(String text) {
        String normalized = TextUtil.normalizeSearchText(text);
        if (TextUtil.containsAny(normalized, "error", "fail", "failure", "timeout", "down", "diagnose", "troubleshoot")) {
            return "Troubleshooting";
        }
        if (TextUtil.containsAny(normalized, "configure", "configuration", "setting", "yaml", "property")) {
            return "Configuration";
        }
        if (TextUtil.containsAny(normalized, "workflow", "process", "pipeline", "step")) {
            return "Workflow";
        }
        return "Concept";
    }

    private List<String> extractConcepts(String text) {
        String normalized = TextUtil.normalizeSearchText(text);
        List<String> concepts = new ArrayList<>();
        for (String term : normalized.split("[^a-z0-9+#./-]+")) {
            if (term.length() >= 4 && !concepts.contains(term)) {
                concepts.add(term);
            }
            if (concepts.size() >= 12) {
                break;
            }
        }
        return concepts;
    }
}
