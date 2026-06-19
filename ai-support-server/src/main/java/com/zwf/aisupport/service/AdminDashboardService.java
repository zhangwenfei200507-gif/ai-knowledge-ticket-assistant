package com.zwf.aisupport.service;

import com.zwf.aisupport.vo.AdminDashboardOverviewVO;
import com.zwf.aisupport.vo.AdminOperationsCenterVO;

public interface AdminDashboardService {

    AdminDashboardOverviewVO getOverview();

    AdminOperationsCenterVO getOperationsCenter();
}
