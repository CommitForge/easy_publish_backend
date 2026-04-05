package com.easypublish.controller;

import com.easypublish.service.AnalyticsDashboardService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Value("${app.domain.default-public:izipublish.com}")
    private String defaultPublicDomain;

    private final AnalyticsDashboardService analyticsDashboardService;

    public AnalyticsController(AnalyticsDashboardService analyticsDashboardService) {
        this.analyticsDashboardService = analyticsDashboardService;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(
            @RequestParam String userAddress,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String timezone,
            @RequestParam(required = false) Integer topN,
            @RequestParam(required = false) String containerId,
            @RequestParam(required = false) String dataTypeId,
            @RequestParam(required = false) String drilldownDimension,
            @RequestParam(required = false) String drilldownKey,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) Integer graphLimit
    ) {
        if (domain != null && domain.equals(defaultPublicDomain)) {
            domain = null;
        }

        return analyticsDashboardService.buildDashboard(
                userAddress,
                granularity,
                from,
                to,
                timezone,
                topN,
                containerId,
                dataTypeId,
                drilldownDimension,
                drilldownKey,
                domain,
                graphLimit
        );
    }
}
