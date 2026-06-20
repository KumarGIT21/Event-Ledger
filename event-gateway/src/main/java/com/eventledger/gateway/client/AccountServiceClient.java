package com.eventledger.gateway.client;

import com.eventledger.gateway.dto.BalanceResponse;
import com.eventledger.gateway.observability.TraceContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceClient.class);
    public static final String TRACE_HEADER = "X-Trace-Id";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AccountServiceClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${account-service.base-url}") String baseUrl,
            @Value("${account-service.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${account-service.read-timeout-ms:3000}") long readTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.baseUrl = baseUrl;
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "applyTransactionFallback")
    @Retry(name = "accountService")
    public void applyTransaction(String accountId, ApplyTransactionPayload payload) {
        String url = baseUrl + "/accounts/" + accountId + "/transactions";
        HttpHeaders headers = traceHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ApplyTransactionPayload> entity = new HttpEntity<>(payload, headers);

        log.info("Calling Account Service applyTransaction accountId={} eventId={} traceId={}",
                accountId, payload.eventId(), TraceContext.getTraceId());

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
        } catch (RestClientException ex) {
            throw new AccountServiceException("Account Service unavailable: " + ex.getMessage(), ex);
        }
    }

    @CircuitBreaker(name = "accountService", fallbackMethod = "getBalanceFallback")
    @Retry(name = "accountService")
    public BalanceResponse getBalance(String accountId) {
        String url = baseUrl + "/accounts/" + accountId + "/balance";
        HttpHeaders headers = traceHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        log.info("Calling Account Service getBalance accountId={} traceId={}",
                accountId, TraceContext.getTraceId());

        try {
            ResponseEntity<BalanceResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, BalanceResponse.class);
            return response.getBody();
        } catch (RestClientException ex) {
            throw new AccountServiceException("Account Service unavailable: " + ex.getMessage(), ex);
        }
    }

    private HttpHeaders traceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String traceId = TraceContext.getTraceId();
        if (traceId != null) {
            headers.set(TRACE_HEADER, traceId);
        }
        return headers;
    }

    @SuppressWarnings("unused")
    private void applyTransactionFallback(String accountId, ApplyTransactionPayload payload, Throwable ex) {
        log.warn("Account Service circuit open or failed for eventId={}: {}", payload.eventId(), ex.getMessage());
        throw new AccountServiceException("Account Service unavailable", ex);
    }

    @SuppressWarnings("unused")
    private BalanceResponse getBalanceFallback(String accountId, Throwable ex) {
        log.warn("Account Service circuit open or failed for balance accountId={}: {}", accountId, ex.getMessage());
        throw new AccountServiceException("Account Service unavailable", ex);
    }
}
