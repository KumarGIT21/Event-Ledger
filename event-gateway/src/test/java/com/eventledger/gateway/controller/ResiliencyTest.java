package com.eventledger.gateway.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WireMockTest(httpPort = 18082)
@TestPropertySource(properties = {
        "account-service.base-url=http://localhost:18082",
        "resilience4j.circuitbreaker.instances.accountService.slidingWindowSize=3",
        "resilience4j.circuitbreaker.instances.accountService.minimumNumberOfCalls=2",
        "resilience4j.circuitbreaker.instances.accountService.waitDurationInOpenState=30s",
        "resilience4j.retry.instances.accountService.maxAttempts=1"
})
class ResiliencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returns503WhenAccountServiceIsUnavailable() throws Exception {
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-down/transactions"))
                .willReturn(aResponse().withStatus(503)));

        String body = """
                {
                  "eventId": "evt-down",
                  "accountId": "acct-down",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Account Service")));
    }

    @Test
    void balanceQueryReturns503WhenAccountServiceIsUnavailable() throws Exception {
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/accounts/acct-bal/balance"))
                .willReturn(aResponse().withStatus(503).withFixedDelay(100)));

        mockMvc.perform(get("/accounts/acct-bal/balance"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Account Service")));
    }

    @Test
    void circuitBreakerOpensAfterRepeatedFailures() throws Exception {
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-cb/transactions"))
                .willReturn(aResponse().withStatus(500)));

        String body = """
                {
                  "eventId": "evt-cb-%d",
                  "accountId": "acct-cb",
                  "type": "CREDIT",
                  "amount": 1.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body.formatted(1)))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body.formatted(2)))
                .andExpect(status().isServiceUnavailable());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body.formatted(3)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Account Service")));
    }
}
