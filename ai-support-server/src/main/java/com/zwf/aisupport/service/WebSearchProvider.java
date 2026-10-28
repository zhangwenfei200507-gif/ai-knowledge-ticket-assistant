package com.zwf.aisupport.service;

import java.util.List;

/** Provider boundary for web search. Retrieval and evidence cleaning stay outside the provider. */
public interface WebSearchProvider {

    String name();

    ProviderResult search(String query, int limit);

    record ProviderResult(String provider,
                          List<SearchResult> results,
                          String status,
                          String errorCategory,
                          String safeErrorMessage) {
        public boolean succeeded() {
            return "SUCCEEDED".equals(status) && results != null && !results.isEmpty();
        }
    }

    record SearchResult(String title, String url, String snippet) {
    }
}
