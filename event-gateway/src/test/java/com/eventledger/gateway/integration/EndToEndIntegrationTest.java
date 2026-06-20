package com.eventledger.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.eventledger.account.AccountServiceApplication;
import com.eventledger.gateway.EventGatewayApplication;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        classes = EventGatewayApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:gateway_e2e;DB_CLOSE_DELAY=-1;MODE=MySQL",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@ActiveProfiles("test")
class EndToEndIntegrationTest {

    private static final int ACCOUNT_PORT = findFreePort();
    private static final ConfigurableApplicationContext ACCOUNT_CONTEXT = startAccountService();

    @LocalServerPort
    private int gatewayPort;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + ACCOUNT_PORT);
        registry.add("resilience4j.retry.instances.accountService.maxAttempts", () -> "1");
    }

    @AfterAll
    static void stopAccountService() {
        ACCOUNT_CONTEXT.close();
    }

    @Test
    void fullGatewayToAccountServiceFlow() {
        String eventBody = """
                {
                  "eventId": "evt-e2e",
                  "accountId": "acct-e2e",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(eventBody, headers);

        ResponseEntity<Map> createResponse = restTemplate.postForEntity(
                gatewayUrl("/events"), request, Map.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getHeaders().getFirst("X-Trace-Id")).isNotBlank();

        ResponseEntity<Map> balanceResponse = restTemplate.getForEntity(
                gatewayUrl("/accounts/acct-e2e/balance"), Map.class);

        assertThat(balanceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new java.math.BigDecimal(balanceResponse.getBody().get("balance").toString()))
                .isEqualByComparingTo("150.00");

        ResponseEntity<Map[]> listResponse = restTemplate.getForEntity(
                gatewayUrl("/events?account=acct-e2e"), Map[].class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody()[0]).containsEntry("eventId", "evt-e2e");
    }

    private String gatewayUrl(String path) {
        return "http://localhost:" + gatewayPort + path;
    }

    private static ConfigurableApplicationContext startAccountService() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(AccountServiceApplication.class)
                .run(
                        "--server.port=" + ACCOUNT_PORT,
                        "--spring.datasource.url=jdbc:h2:mem:account_e2e;DB_CLOSE_DELAY=-1;MODE=MySQL",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.jpa.hibernate.ddl-auto=create-drop",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
                );
        waitForHealthy(context);
        return context;
    }

    private static void waitForHealthy(ConfigurableApplicationContext context) {
        TestRestTemplate restTemplate = new TestRestTemplate();
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(
                        "http://localhost:" + ACCOUNT_PORT + "/health", Map.class);
                if (response.getStatusCode().is2xxSuccessful()) {
                    return;
                }
            } catch (Exception ignored) {
                // retry until account service is ready
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for account service", ex);
            }
        }
        context.close();
        throw new IllegalStateException("Account service did not become healthy on port " + ACCOUNT_PORT);
    }

    private static int findFreePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to find free port", ex);
        }
    }
}
