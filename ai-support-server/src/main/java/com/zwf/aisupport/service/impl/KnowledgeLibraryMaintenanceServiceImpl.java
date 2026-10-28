package com.zwf.aisupport.service.impl;

import com.zwf.aisupport.common.util.CurrentUserUtil;
import com.zwf.aisupport.entity.DocumentChunk;
import com.zwf.aisupport.entity.KnowledgeArticle;
import com.zwf.aisupport.entity.KnowledgeDocument;
import com.zwf.aisupport.mapper.DocumentChunkMapper;
import com.zwf.aisupport.mapper.KnowledgeArticleMapper;
import com.zwf.aisupport.mapper.KnowledgeDocumentMapper;
import com.zwf.aisupport.service.KnowledgeLibraryMaintenanceService;
import com.zwf.aisupport.vo.KnowledgeLibraryRebuildResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class KnowledgeLibraryMaintenanceServiceImpl implements KnowledgeLibraryMaintenanceService {

    private static final String MAINTENANCE_SOURCE = "Knowledge Library Maintenance";

    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgePackReorganizer knowledgePackReorganizer;
    private final SemanticChunkManagementStage chunkManagementStage;
    private final KnowledgeArticleChunkManager articleChunkManager;

    public KnowledgeLibraryMaintenanceServiceImpl(KnowledgeArticleMapper knowledgeArticleMapper,
                                                  KnowledgeDocumentMapper knowledgeDocumentMapper,
                                                  DocumentChunkMapper documentChunkMapper,
                                                  KnowledgePackReorganizer knowledgePackReorganizer,
                                                  SemanticChunkManagementStage chunkManagementStage,
                                                  KnowledgeArticleChunkManager articleChunkManager) {
        this.knowledgeArticleMapper = knowledgeArticleMapper;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgePackReorganizer = knowledgePackReorganizer;
        this.chunkManagementStage = chunkManagementStage;
        this.articleChunkManager = articleChunkManager;
    }

    @Override
    @Transactional
    public KnowledgeLibraryRebuildResultVO rebuildArticlesAndChunks() {
        CurrentUserUtil.requireAdmin("Only administrators can rebuild the knowledge library");

        KnowledgeLibraryRebuildResultVO result = new KnowledgeLibraryRebuildResultVO();
        rebuildArticles(result);
        rebuildDocumentChunks(result);
        return result;
    }

    private void rebuildArticles(KnowledgeLibraryRebuildResultVO result) {
        List<KnowledgeArticle> rewrittenArticles = new ArrayList<>();
        for (KnowledgeArticle article : knowledgeArticleMapper.findPublishedArticles()) {
            KnowledgeArticle rewritten = knowledgePackReorganizer.rewriteSingleArticle(
                    article.getId(),
                    article.getTitle(),
                    article.getCategory(),
                    article.getContent(),
                    MAINTENANCE_SOURCE
            );
            rewrittenArticles.add(rewritten);
            result.setChunksCreated(result.getChunksCreated() + articleChunkManager.rebuildArticleChunks(rewritten));
            result.setArticlesRewritten(result.getArticlesRewritten() + 1);
        }

        Map<String, List<KnowledgeArticle>> articlesByDomain = groupByDomain(rewrittenArticles);
        for (Map.Entry<String, List<KnowledgeArticle>> entry : articlesByDomain.entrySet()) {
            List<KnowledgeArticle> domainArticles = entry.getValue();
            if (domainArticles.isEmpty()) {
                continue;
            }

            result.getRebuiltDomains().add(domainArticles.get(0).getCategory());
            if (domainArticles.size() == 1) {
                continue;
            }

            KnowledgeArticle primary = choosePrimaryArticle(domainArticles);
            for (KnowledgeArticle duplicate : domainArticles) {
                if (duplicate.getId().equals(primary.getId())) {
                    continue;
                }

                primary = knowledgePackReorganizer.reorganizeExistingById(
                        primary.getId(),
                        duplicate.getTitle(),
                        duplicate.getCategory(),
                        duplicate.getContent(),
                        MAINTENANCE_SOURCE
                );
                result.setChunksCreated(result.getChunksCreated() + articleChunkManager.rebuildArticleChunks(primary));
                knowledgeArticleMapper.archive(duplicate.getId());
                articleChunkManager.archiveArticleChunks(duplicate.getId());
                result.setDuplicateArticlesArchived(result.getDuplicateArticlesArchived() + 1);
            }
        }
    }

    private void rebuildDocumentChunks(KnowledgeLibraryRebuildResultVO result) {
        for (KnowledgeDocument document : knowledgeDocumentMapper.findActiveDocuments()) {
            List<DocumentChunk> activeChunks = documentChunkMapper.findByDocumentId(document.getId());
            if (activeChunks.isEmpty()) {
                continue;
            }

            String combinedContent = combineChunks(activeChunks);
            List<String> rebuiltChunks = chunkManagementStage.splitDocument(combinedContent);
            result.setChunksArchived(result.getChunksArchived() + documentChunkMapper.archiveActiveByDocumentId(document.getId()));

            for (int i = 0; i < rebuiltChunks.size(); i++) {
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocumentId(document.getId());
                chunk.setChunkIndex(i);
                chunk.setContent(rebuiltChunks.get(i));
                documentChunkMapper.insert(chunk);
                result.setChunksCreated(result.getChunksCreated() + 1);
            }
            result.setDocumentsProcessed(result.getDocumentsProcessed() + 1);
        }
    }

    private Map<String, List<KnowledgeArticle>> groupByDomain(List<KnowledgeArticle> articles) {
        Map<String, List<KnowledgeArticle>> articlesByDomain = new LinkedHashMap<>();
        for (KnowledgeArticle article : articles) {
            articlesByDomain.computeIfAbsent(normalizeDomain(article.getCategory()), ignored -> new ArrayList<>())
                    .add(article);
        }
        return articlesByDomain;
    }

    private KnowledgeArticle choosePrimaryArticle(List<KnowledgeArticle> articles) {
        return articles.stream()
                .max(Comparator.comparingInt(article -> article.getContent() == null ? 0 : article.getContent().length()))
                .orElse(articles.get(0));
    }

    private String combineChunks(List<DocumentChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        chunks.stream()
                .sorted(Comparator.comparing(DocumentChunk::getChunkIndex))
                .forEach(chunk -> {
                    if (!builder.isEmpty()) {
                        builder.append("\n\n");
                    }
                    builder.append(chunk.getContent());
                });
        return builder.toString().trim();
    }

    private String normalizeDomain(String category) {
        return (category == null || category.isBlank() ? "General" : category)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
