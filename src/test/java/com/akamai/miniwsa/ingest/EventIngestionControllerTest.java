package com.akamai.miniwsa.ingest;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end API test for the ingestion endpoint: real controller, real
 * EventIngestionService, real Validator. Proves that zero-trust validation in
 * the core surfaces as a proper 400 over the wire, and that a clean event is
 * accepted with 201. This is the Part 1 API integration test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String VALID_EVENT = """
            {
              "eventId": "evt-00132",
              "timestamp": "2026-05-20T14:32:10Z",
              "configId": 14227,
              "policyId": "pol_web1",
              "clientIp": "203.0.113.42",
              "hostname": "www.example.com",
              "path": "/api/v1/login",
              "method": "POST",
              "statusCode": 403,
              "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
              "rule": {
                "id": "950001",
                "name": "SQL_INJECTION",
                "message": "SQL Injection Attack Detected",
                "severity": "CRITICAL",
                "category": "INJECTION"
              },
              "action": "DENY",
              "geoLocation": { "country": "CN", "city": "Beijing" },
              "requestSize": 1024,
              "responseSize": 256
            }
            """;

    // Deserializes fine (valid enums/timestamp) but fails semantic validation:
    // eventId is blank and clientIp is absent.
    private static final String INVALID_EVENT = """
            {
              "eventId": "",
              "timestamp": "2026-05-20T14:32:10Z",
              "configId": 14227,
              "policyId": "pol_web1",
              "hostname": "www.example.com",
              "path": "/api/v1/login",
              "method": "POST",
              "statusCode": 403,
              "userAgent": "Mozilla/5.0",
              "rule": {
                "id": "950001",
                "name": "SQL_INJECTION",
                "message": "SQL Injection Attack Detected",
                "severity": "CRITICAL",
                "category": "INJECTION"
              },
              "action": "DENY",
              "geoLocation": { "country": "CN", "city": "Beijing" },
              "requestSize": 1024,
              "responseSize": 256
            }
            """;

    @Test
    void acceptsValidEventWith201() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-00132"));
    }

    @Test
    void rejectsInvalidEventWith400AndFieldErrors() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(INVALID_EVENT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("eventId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("clientIp")));
    }
}
