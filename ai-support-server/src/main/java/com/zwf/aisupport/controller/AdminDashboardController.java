package com.zwf.aisupport.controller;

import com.zwf.aisupport.common.result.Result;
import com.zwf.aisupport.service.AdminDashboardService;
import com.zwf.aisupport.vo.AdminDashboardOverviewVO;
import com.zwf.aisupport.vo.AdminOperationsCenterVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/overview")
    public Result<AdminDashboardOverviewVO> getOverview() {
        AdminDashboardOverviewVO overview = adminDashboardService.getOverview();
        return Result.success(overview);
    }

    @GetMapping("/operations-center")
    public Result<AdminOperationsCenterVO> getOperationsCenter() {
        AdminOperationsCenterVO operationsCenter = adminDashboardService.getOperationsCenter();
        return Result.success(operationsCenter);
    }
}
