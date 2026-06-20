package com.eventledger.account.controller;

import com.eventledger.account.observability.MetricsService;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final DataSource dataSource;
    private final MetricsService metricsService;

    public HealthController(DataSource dataSource, MetricsService metricsService) {
        this.dataSource = dataSource;
        this.metricsService = metricsService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("service", "account-service");
        body.put("database", checkDatabase());
        body.put("metrics", metricsService.snapshot());
        return ResponseEntity.ok(body);
    }

    private String checkDatabase() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception ex) {
            return "DOWN";
        }
    }
}
