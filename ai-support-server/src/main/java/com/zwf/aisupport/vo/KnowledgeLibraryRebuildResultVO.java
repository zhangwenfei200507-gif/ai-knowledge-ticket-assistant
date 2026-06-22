package com.zwf.aisupport.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class KnowledgeLibraryRebuildResultVO {

    private int articlesRewritten;
    private int duplicateArticlesArchived;
    private int documentsProcessed;
    private int chunksArchived;
    private int chunksCreated;
    private List<String> rebuiltDomains = new ArrayList<>();
}
