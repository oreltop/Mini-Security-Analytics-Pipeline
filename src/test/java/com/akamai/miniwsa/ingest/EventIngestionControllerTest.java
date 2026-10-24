package com.akamai.miniwsa.ingest;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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
        // A single object binds as a 1-element list; the response is that list.
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].eventId").value("evt-00132"));
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

    @Test
    void rejectsUnknownEnumWith400InOurErrorShape() throws Exception {
        String badEnum = VALID_EVENT.replace("\"category\": \"INJECTION\"", "\"category\": \"BANANA\"");

        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badEnum))
                .andExpect(status().isBadRequest())
                // list binding → the error names the batch index and field path
                .andExpect(jsonPath("$.errors[*].field", hasItem("[0].rule.category")));
    }

    @Test
    void rejectsMalformedJsonWith400InOurErrorShape() throws Exception {
        String malformed = "{ \"eventId\": \"evt-1\", ";

        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("body")));
    }

    @Test
    void acceptsBatchOfEventsWith201() throws Exception {
        String second = VALID_EVENT.replace("evt-00132", "evt-00133");
        String batch = "[" + VALID_EVENT + "," + second + "]";

        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventId").value("evt-00132"))
                .andExpect(jsonPath("$[1].eventId").value("evt-00133"))
                .andExpect(jsonPath("$[0].attackType").value("SQL/Command Injection"));
    }

    @Test
    void rejectsEventMissingNumericFieldsWith400() throws Exception {
        // configId and statusCode omitted entirely must NOT be silently defaulted to 0.
        String missingNumbers = """
                {
                  "eventId": "evt-1",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "policyId": "pol_web1",
                  "clientIp": "203.0.113.42",
                  "hostname": "www.example.com",
                  "path": "/api/v1/login",
                  "method": "POST",
                  "userAgent": "Mozilla/5.0",
                  "rule": { "id": "950001", "name": "SQL_INJECTION", "message": "SQLi", "severity": "CRITICAL", "category": "INJECTION" },
                  "action": "DENY",
                  "geoLocation": { "country": "CN", "city": "Beijing" },
                  "requestSize": 1024,
                  "responseSize": 256
                }
                """;

        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingNumbers))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("configId")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("statusCode")));
    }
}
