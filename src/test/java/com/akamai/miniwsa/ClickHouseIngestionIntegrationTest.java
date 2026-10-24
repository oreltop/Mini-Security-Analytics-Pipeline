package com.akamai.miniwsa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.akamai.miniwsa.storage.EventRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Full-stack integration test on the "clickhouse" profile: boots the whole app
 * against a real (Testcontainers) ClickHouse and verifies an ingested event is
 * actually persisted there. Tagged "integration" (excluded from the default suite;
 * run with -Pintegration).
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("clickhouse")
class ClickHouseIngestionIntegrationTest {

    @Container
    static final ClickHouseContainer CLICKHOUSE =
            new ClickHouseContainer(DockerImageName.parse("clickhouse/clickhouse-server:24.3"))
                    .withUsername("default")
                    .withPassword("test");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", CLICKHOUSE::getJdbcUrl);
        registry.add("spring.datasource.username", CLICKHOUSE::getUsername);
        registry.add("spring.datasource.password", CLICKHOUSE::getPassword);
        registry.add("spring.datasource.driver-class-name", CLICKHOUSE::getDriverClassName);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository repository;

    private static final String VALID_EVENT = """
            {
              "eventId": "evt-ch-1",
              "timestamp": "2026-05-20T14:32:10Z",
              "configId": 14227,
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
            """;

    @Test
    void ingestedEventIsPersistedInClickHouse() throws Exception {
        mockMvc.perform(post("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_EVENT))
                .andExpect(status().isCreated());

        assertThat(repository).isInstanceOf(
                com.akamai.miniwsa.storage.ClickHouseEventRepository.class);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findAll().get(0).event().eventId()).isEqualTo("evt-ch-1");
        assertThat(repository.findAll().get(0).threatScore()).isEqualTo(75);
    }
}
