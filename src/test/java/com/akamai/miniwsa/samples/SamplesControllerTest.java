package com.akamai.miniwsa.samples;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SamplesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void samplesReturnsEnvelopeWithTotal() throws Exception {
        ingest("evt-sample-1");
        ingest("evt-sample-2");

        mockMvc.perform(get("/v1/events/samples")
                        .param("configId", "888001")
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-21T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    private void ingest(String eventId) throws Exception {
        String json = """
                {
                  "eventId": "%s",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "configId": 888001,
                  "policyId": "pol_web1",
                  "clientIp": "203.0.113.42",
                  "hostname": "www.example.com",
                  "path": "/api/v1/login",
                  "method": "POST",
                  "statusCode": 403,
                  "userAgent": "Mozilla/5.0",
                  "rule": { "id": "950001", "name": "SQL_INJECTION", "message": "SQLi", "severity": "CRITICAL", "category": "INJECTION" },
                  "action": "DENY",
                  "geoLocation": { "country": "CN", "city": "Beijing" },
                  "requestSize": 1024,
                  "responseSize": 256
                }
                """.formatted(eventId);
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }
}
