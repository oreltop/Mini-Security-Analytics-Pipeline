package com.akamai.miniwsa.ratelimit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.rate-limit.requests-per-minute=3")
class AnalyticsRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void analyticsRequestsReturn429AfterLimit() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/stats/summary")
                            .param("from", "2026-05-20T00:00:00Z")
                            .param("to", "2026-05-21T00:00:00Z"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/v1/stats/summary")
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-21T00:00:00Z"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.errors[0].message", containsString("Rate limit exceeded")));
    }

    @Test
    void ingestIsNotRateLimited() throws Exception {
        String json = """
                {
                  "eventId": "evt-rl-ingest",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "configId": 999001,
                  "policyId": "pol_web1",
                  "clientIp": "198.51.100.99",
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
                """;
        for (int i = 0; i < 5; i++) {
            String body = json.replace("evt-rl-ingest", "evt-rl-ingest-" + i);
            mockMvc.perform(post("/v1/events/ingest")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated());
        }
    }
}
