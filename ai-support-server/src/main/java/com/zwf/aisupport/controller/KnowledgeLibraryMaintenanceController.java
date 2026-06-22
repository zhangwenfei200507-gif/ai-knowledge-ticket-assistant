package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.service.KnowledgeLibraryMaintenanceService;
import com.zwf.aisupport.vo.KnowledgeLibraryRebuildResultVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/knowledge-maintenance")
public class KnowledgeLibraryMaintenanceController {

    private final KnowledgeLibraryMaintenanceService maintenanceService;

    public KnowledgeLibraryMaintenanceController(KnowledgeLibraryMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping("/rebuild")
    public Result<KnowledgeLibraryRebuildResultVO> rebuildArticlesAndChunks() {
        return Result.success(maintenanceService.rebuildArticlesAndChunks());
    }
}
