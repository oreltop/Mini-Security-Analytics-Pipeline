package com.akamai.miniwsa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

// DataSource auto-config is excluded so the default (in-memory) profile needs no
// database to boot; the ClickHouse profile wires its own DataSource + JdbcTemplate.
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class MiniWsaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniWsaApplication.class, args);
    }
}
