package com.akamai.miniwsa.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Verifies that an incoming DLR payload (the wire format from the assignment)
 * deserializes into our typed domain model. The value here is in the contract,
 * not in Jackson's plumbing: category/action/severity are strong enums, so a
 * valid payload must map "INJECTION" to RuleCategory.INJECTION.
 */
class SecurityEventDeserializationTest {

    // Mirror Spring Boot's auto-configured mapper: discover modules on the
    // classpath (e.g. JavaTimeModule) so Instant and friends deserialize.
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesSampleDlrIntoTypedModel() throws Exception {
        String json = """
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
                  "geoLocation": {
                    "country": "CN",
                    "city": "Beijing"
                  },
                  "requestSize": 1024,
                  "responseSize": 256
                }
                """;

        SecurityEvent event = objectMapper.readValue(json, SecurityEvent.class);

        assertThat(event.eventId()).isEqualTo("evt-00132");
        assertThat(event.configId()).isEqualTo(14227);
        assertThat(event.statusCode()).isEqualTo(403);
        assertThat(event.action()).isEqualTo(Action.DENY);
        assertThat(event.rule().severity()).isEqualTo(Severity.CRITICAL);
        assertThat(event.rule().category()).isEqualTo(RuleCategory.INJECTION);
        assertThat(event.geoLocation().country()).isEqualTo("CN");
    }
}
