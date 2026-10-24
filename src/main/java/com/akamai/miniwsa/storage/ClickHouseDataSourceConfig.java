package com.akamai.miniwsa.storage;

import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the DataSource and JdbcTemplate for the "clickhouse" profile, and creates
 * the events table on startup. DataSource auto-configuration is excluded app-wide
 * (so the default in-memory profile boots without a database), so we build it here
 * from {@code spring.datasource.*}. The schema is applied via JdbcTemplate rather
 * than spring.sql.init, whose script parser mishandles the multi-line ClickHouse DDL.
 */
@Configuration
@Profile("clickhouse")
public class ClickHouseDataSourceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${spring.datasource.url}") String url,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password,
            @Value("${spring.datasource.driver-class-name}") String driverClassName) {
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public ApplicationRunner clickHouseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        return args -> {
            String ddl = new String(
                    new ClassPathResource("clickhouse-schema.sql").getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            jdbcTemplate.execute(ddl);
        };
    }
}
