package com.zwf.aisupport.service.impl;

import java.util.List;

public record KnowledgeClassification(String domain, String type, List<String> concepts) {
}
