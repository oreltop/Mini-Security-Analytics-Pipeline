package com.akamai.miniwsa.stats;

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

/**
 * API test for the stats summary endpoint (in-memory profile). Ingests events and
 * checks the aggregate for a config + time range.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StatsSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void summaryReportsTotalEventsForConfigAndRange() throws Exception {
        ingest("evt-s1");
        ingest("evt-s2");

        mockMvc.perform(get("/v1/stats/summary")
                        .param("configId", "777001")
                        .param("from", "2026-05-20T00:00:00Z")
                        .param("to", "2026-05-21T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configId").value(777001))
                .andExpect(jsonPath("$.totalEvents").value(2))
                .andExpect(jsonPath("$.timeRange.from").value("2026-05-20T00:00:00Z"))
                .andExpect(jsonPath("$.timeRange.to").value("2026-05-21T00:00:00Z"))
                .andExpect(jsonPath("$.byCategory.INJECTION.count").value(2))
                .andExpect(jsonPath("$.byCategory.INJECTION.avgThreatScore").value(75.0))
                .andExpect(jsonPath("$.byAction.DENY").value(2))
                .andExpect(jsonPath("$.topAttackers[0].clientIp").value("198.51.100.77"))
                .andExpect(jsonPath("$.topAttackers[0].count").value(2))
                .andExpect(jsonPath("$.topAttackers[0].avgThreatScore").value(75.0))
                .andExpect(jsonPath("$.topTargetedPaths[0].path").value("/api/v1/login"))
                .andExpect(jsonPath("$.topTargetedPaths[0].count").value(2));
    }

    private void ingest(String eventId) throws Exception {
        String json = """
                {
                  "eventId": "%s",
                  "timestamp": "2026-05-20T14:32:10Z",
                  "configId": 777001,
                  "policyId": "pol_web1",
                  "clientIp": "198.51.100.77",
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
