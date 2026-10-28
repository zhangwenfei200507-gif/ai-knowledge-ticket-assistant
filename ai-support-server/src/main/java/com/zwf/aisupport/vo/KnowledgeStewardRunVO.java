package com.zwf.aisupport.vo;

import lombok.Data;

@Data
public class KnowledgeStewardRunVO {

    private int scannedWebEvidence;
    private int scannedDocumentCandidates;
    private int scannedQuestionLogs;
    private int createdTasks;
    private int skippedExistingTasks;
    private int skippedLowQuality;
    private int llmCallsAllowed;
}
