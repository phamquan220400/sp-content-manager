package com.samuel.app.creator.controller;

import com.samuel.app.creator.dto.DashboardResponse;
import com.samuel.app.creator.service.DashboardService;
import com.samuel.app.shared.controller.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        log.info("Dashboard requested for userId={}", userId);
        DashboardResponse dashboardData = dashboardService.getDashboardData(userId);
        log.debug("Dashboard response built for userId={}", userId);
        return ResponseEntity.ok(ApiResponse.ok(dashboardData));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<String>> getSettings(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("Settings stub accessed for userId={}", userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Account settings — coming soon."));
    }
}
