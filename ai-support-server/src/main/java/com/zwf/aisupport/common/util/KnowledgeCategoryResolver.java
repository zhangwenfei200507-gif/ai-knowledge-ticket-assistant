package com.zwf.aisupport.common.util;

public final class KnowledgeCategoryResolver {

    private KnowledgeCategoryResolver() {
    }

    public static String infer(String text) {
        return infer("General", text);
    }

    public static String infer(String fallbackCategory, String text) {
        String normalized = TextUtil.normalizeSearchText(text);
        if (TextUtil.containsAny(normalized, "jwt", "token", "login", "auth", "password", "session")) {
            return "Auth / JWT";
        }
        if (TextUtil.containsAny(normalized, "redis", "cache")) {
            return "Redis Cache";
        }
        if (TextUtil.containsAny(normalized, "postgres", "postgresql", "database", "sql")) {
            return "PostgreSQL";
        }
        if (TextUtil.containsAny(normalized, "docker", "container", "compose")) {
            return "Docker";
        }
        if (TextUtil.containsAny(normalized, "kubernetes", "k8s", "pod", "cluster")) {
            return "Kubernetes";
        }
        if (TextUtil.containsAny(normalized, "incident", "outage", "sev", "rollback")) {
            return "Incident SOP";
        }
        if (TextUtil.containsAny(normalized, "ticket", "sla", "priority", "escalation")) {
            return "Ticket Rules";
        }
        if (TextUtil.containsAny(normalized, "customer", "template", "reply")) {
            return "Customer Templates";
        }
        return fallbackCategory == null || fallbackCategory.isBlank() ? "General" : fallbackCategory.trim();
    }
}
