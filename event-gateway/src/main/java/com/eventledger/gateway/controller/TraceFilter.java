package com.eventledger.gateway.controller;

import com.eventledger.gateway.observability.MetricsService;
import com.eventledger.gateway.observability.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    public static final String TRACE_HEADER = "X-Trace-Id";

    private final MetricsService metricsService;

    public TraceFilter(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceContext.generateTraceId();
        TraceContext.setTraceId(traceId);
        response.setHeader(TRACE_HEADER, traceId);

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            metricsService.recordRequest(request.getRequestURI(), response.getStatus());
            metricsService.recordLatency(request.getRequestURI(), System.nanoTime() - start);
            TraceContext.clear();
        }
    }
}
