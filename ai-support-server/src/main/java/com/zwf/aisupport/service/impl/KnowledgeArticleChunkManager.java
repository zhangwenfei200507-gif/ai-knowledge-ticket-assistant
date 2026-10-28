package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.TextUtil;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.KnowledgeArticleChunk;
import com.zwf.aisupport.mapper.KnowledgeArticleChunkMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KnowledgeArticleChunkManager {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeArticleChunkManager.class);

    private static final Pattern SECTION_HEADING_PATTERN = Pattern.compile("(?m)^##\\s+(.+?)\\s*$");
    private static final int MAX_SECTION_CHARS = 2600;
    private static final Set<String> STOP_WORDS = Set.of(
            "this", "that", "with", "from", "when", "then", "should", "must", "will", "into",
            "knowledge", "support", "aegiscloud", "article", "section", "source", "reviewed",
            "internal", "external", "system", "service", "services", "using", "used", "uses"
    );

    private final KnowledgeArticleChunkMapper chunkMapper;
    private final SemanticChunkManagementStage chunkManagementStage;

    public KnowledgeArticleChunkManager(KnowledgeArticleChunkMapper chunkMapper,
                                        SemanticChunkManagementStage chunkManagementStage) {
        this.chunkMapper = chunkMapper;
        this.chunkManagementStage = chunkManagementStage;
    }

    public int rebuildArticleChunks(KnowledgeArticle article) {
        if (article == null || article.getId() == null || article.getContent() == null || article.getContent().isBlank()) {
            return 0;
        }

        try {
            chunkMapper.archiveActiveByArticleId(article.getId());
            List<ArticleSection> sections = splitSections(article.getContent());
            int index = 0;
            for (ArticleSection section : sections) {
                for (String content : splitSectionContent(section)) {
                    KnowledgeArticleChunk chunk = new KnowledgeArticleChunk();
                    chunk.setArticleId(article.getId());
                    chunk.setChunkIndex(index++);
                    chunk.setSectionTitle(TextUtil.limit(section.title(), 160));
                    chunk.setSectionType(sectionType(section.title()));
                    chunk.setConceptTags(extractConceptTags(article, section, content));
                    chunk.setContent(content);
                    chunkMapper.insert(chunk);
                }
            }
            return index;
        } catch (Exception exception) {
            log.warn("Article chunk rebuild failed for articleId={}: {}", article.getId(), exception.getMessage());
            return 0;
        }
    }

    public int archiveArticleChunks(Long articleId) {
        if (articleId == null) {
            return 0;
        }
        try {
            return chunkMapper.archiveActiveByArticleId(articleId);
        } catch (Exception exception) {
            log.warn("Article chunk archive failed for articleId={}: {}", articleId, exception.getMessage());
            return 0;
        }
    }

    private List<ArticleSection> splitSections(String content) {
        String normalized = content.trim();
        Matcher matcher = SECTION_HEADING_PATTERN.matcher(normalized);
        List<ArticleSection> sections = new ArrayList<>();
        String currentTitle = "Overview";
        int currentStart = 0;

        while (matcher.find()) {
            if (matcher.start() > currentStart) {
                addSection(sections, currentTitle, normalized.substring(currentStart, matcher.start()));
            }
            currentTitle = cleanSectionTitle(matcher.group(1));
            currentStart = matcher.end();
        }

        if (currentStart < normalized.length()) {
            addSection(sections, currentTitle, normalized.substring(currentStart));
        }

        if (sections.isEmpty()) {
            addSection(sections, "Overview", normalized);
        }
        return sections;
    }

    private void addSection(List<ArticleSection> sections, String title, String body) {
        String cleanedBody = cleanSectionBody(body);
        if (!cleanedBody.isBlank()) {
            sections.add(new ArticleSection(cleanSectionTitle(title), cleanedBody));
        }
    }

    private String cleanSectionTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Overview";
        }
        String cleaned = title.replaceAll("(?i)\\b(runbooks?|playbooks?|sops?)\\b", "")
                .replaceAll("\\s+[-:]+\\s*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.isBlank() ? "Overview" : cleaned;
    }

    private List<String> splitSectionContent(ArticleSection section) {
        String content = "## " + section.title() + "\n\n" + section.body();
        if (content.length() <= MAX_SECTION_CHARS) {
            return List.of(content.trim());
        }
        return chunkManagementStage.splitDocument(content);
    }

    private String cleanSectionBody(String body) {
        if (body == null) {
            return "";
        }
        return body
                .replaceAll("(?im)^#\\s+.*$", "")
                .replaceAll("(?im)^Category\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Purpose\\s*:?\\s*.*$", "")
                .replaceAll("(?im)^Primary keywords\\s*:\\s*.*$", "")
                .replaceAll("(?im)^Source references\\s*:?\\s*$", "")
                .replaceAll("(?im)^\\s*-?\\s*https?://\\S+\\s*$", "")
                .replaceAll("(?im)^.*\\b(uploaded document chunks remain retrieval evidence|durable answer source)\\b.*$", "")
                .replaceAll("(?im)^.*verified operational concept behind the user's question.*$", "")
                .replaceAll("(?im)^.*should be integrated into the closest existing Knowledge Pack.*$", "")
                .replaceAll("(?im)^.*if the evidence is only partially related.*$", "")
                .replaceAll("(?im)^.*remove assumptions.*$", "")
                .replaceAll("(?im)^.*keep one concept per section.*$", "")
                .replaceAll("(?im)^.*keep the final article readable as one official document.*$", "")
                .replaceAll("(?im)^Recommended response for users\\s*$", "")
                .replaceAll("(?im)^.*regardless of what schema is used.*$", "")
                .replaceAll("(?im)^.*\\bsource references\\b.*$", "")
                .replaceAll("(?i)\\b(runbooks?|playbooks?|sops?)\\b", "")
                .replaceAll("\\s+([,.;:])", "$1")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String sectionType(String title) {
        String normalized = TextUtil.safeLower(title);
        if (TextUtil.containsAny(normalized, "configuration", "config", "setting", "parameter")) {
            return "CONFIGURATION";
        }
        if (TextUtil.containsAny(normalized, "workflow", "procedure", "step", "pipeline")) {
            return "WORKFLOW";
        }
        if (TextUtil.containsAny(normalized, "troubleshooting", "issue", "failure", "incident", "diagnos")) {
            return "TROUBLESHOOTING";
        }
        if (TextUtil.containsAny(normalized, "best practice", "prevention", "guideline", "governance")) {
            return "BEST_PRACTICE";
        }
        if (TextUtil.containsAny(normalized, "faq", "question")) {
            return "FAQ";
        }
        return "CONCEPT";
    }

    private String extractConceptTags(KnowledgeArticle article, ArticleSection section, String content) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addTag(tags, article.getCategory());
        addTerms(tags, section.title());
        addTerms(tags, content);
        return TextUtil.limit(String.join(", ", tags), 500);
    }

    private void addTerms(LinkedHashSet<String> tags, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        for (String term : TextUtil.safeLower(value).split("[^a-z0-9+#./_-]+")) {
            if (tags.size() >= 18) {
                return;
            }
            if (isUsefulTag(term)) {
                tags.add(term);
            }
        }
    }

    private void addTag(LinkedHashSet<String> tags, String value) {
        if (value != null && !value.isBlank()) {
            tags.add(value.trim());
        }
    }

    private boolean isUsefulTag(String term) {
        return term != null
                && term.length() >= 3
                && !STOP_WORDS.contains(term)
                && (term.matches(".*[0-9+#./_-].*") || term.length() >= 5);
    }

    private record ArticleSection(String title, String body) {
    }
}
