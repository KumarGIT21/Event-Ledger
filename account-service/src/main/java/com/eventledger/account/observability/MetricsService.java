package com.eventledger.account.observability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MetricsService {

    private final Map<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> latencyTotalsNanos = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> latencySamples = new ConcurrentHashMap<>();

    public void recordRequest(String endpoint, int status) {
        String key = normalizeEndpoint(endpoint);
        requestCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        if (status >= 400) {
            errorCounts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
        }
    }

    public void recordLatency(String endpoint, long durationNanos) {
        String key = normalizeEndpoint(endpoint);
        latencyTotalsNanos.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(durationNanos);
        latencySamples.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        requestCounts.forEach((endpoint, count) -> {
            long errors = errorCounts.getOrDefault(endpoint, new AtomicLong()).get();
            long samples = latencySamples.getOrDefault(endpoint, new AtomicLong()).get();
            long totalNanos = latencyTotalsNanos.getOrDefault(endpoint, new AtomicLong()).get();
            double avgMs = samples == 0 ? 0.0 : (totalNanos / (double) samples) / 1_000_000.0;

            metrics.put(endpoint, Map.of(
                    "requestCount", count.get(),
                    "errorCount", errors,
                    "avgLatencyMs", Math.round(avgMs * 100.0) / 100.0
            ));
        });
        return metrics;
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint.startsWith("/accounts/") && endpoint.endsWith("/transactions")) {
            return "/accounts/{accountId}/transactions";
        }
        if (endpoint.startsWith("/accounts/") && endpoint.endsWith("/balance")) {
            return "/accounts/{accountId}/balance";
        }
        if (endpoint.startsWith("/accounts/")) {
            return "/accounts/{accountId}";
        }
        return endpoint;
    }
}
