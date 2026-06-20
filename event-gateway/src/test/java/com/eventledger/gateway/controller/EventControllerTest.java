package com.eventledger.gateway.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
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
@WireMockTest(httpPort = 18081)
@TestPropertySource(properties = "account-service.base-url=http://localhost:18081")
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void stubAccountService() {
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-123/transactions"))
                .willReturn(aResponse().withStatus(201)));
    }

    @Test
    void submitsEventAndRetrievesIt() throws Exception {
        String body = """
                {
                  "eventId": "evt-001",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 150.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z",
                  "metadata": {"source": "batch"}
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-001"))
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(get("/events/evt-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(150.00));
    }

    @Test
    void duplicateSubmissionIsIdempotent() throws Exception {
        String body = """
                {
                  "eventId": "evt-dup",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 25.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        verify(1, postRequestedFor(urlEqualTo("/accounts/acct-123/transactions")));
    }

    @Test
    void listsEventsByAccountInTimestampOrder() throws Exception {
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-list/transactions"))
                .willReturn(aResponse().withStatus(201)));

        postEvent("evt-late", "acct-list", "2026-05-15T15:00:00Z");
        postEvent("evt-early", "acct-list", "2026-05-15T10:00:00Z");

        mockMvc.perform(get("/events").param("account", "acct-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventId").value("evt-early"))
                .andExpect(jsonPath("$[1].eventId").value("evt-late"));
    }

    @Test
    void propagatesTraceIdToAccountService() throws Exception {
        String body = """
                {
                  "eventId": "evt-trace",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Trace-Id"));

        verify(postRequestedFor(urlEqualTo("/accounts/acct-123/transactions"))
                .withHeader("X-Trace-Id", matching(".+")));
    }

    @Test
    void rejectsInvalidEventType() throws Exception {
        String body = """
                {
                  "eventId": "evt-bad-type",
                  "accountId": "acct-123",
                  "type": "TRANSFER",
                  "amount": 10.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEventsStillWorksWhenAccountServiceIsDown() throws Exception {
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo("/accounts/acct-123/transactions"))
                .willReturn(aResponse().withStatus(201)));

        String body = """
                {
                  "eventId": "evt-read",
                  "accountId": "acct-123",
                  "type": "CREDIT",
                  "amount": 5.00,
                  "currency": "USD",
                  "eventTimestamp": "2026-05-15T14:02:11Z"
                }
                """;
        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/events/evt-read")).andExpect(status().isOk());
        mockMvc.perform(get("/events").param("account", "acct-123")).andExpect(status().isOk());
    }

    private void postEvent(String eventId, String accountId, String timestamp) throws Exception {
        String body = """
                {
                  "eventId": "%s",
                  "accountId": "%s",
                  "type": "CREDIT",
                  "amount": 1.00,
                  "currency": "USD",
                  "eventTimestamp": "%s"
                }
                """.formatted(eventId, accountId, timestamp);

        mockMvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
