package cn.edu.sar.persistence.system;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SystemProbeRepository {
    private final JdbcTemplate jdbc;

    public SystemProbeRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String databaseVersion() { return jdbc.queryForObject("SELECT VERSION()", String.class); }

    public String foundationVersion() {
        return jdbc.queryForObject("SELECT version FROM sar_foundation WHERE id = 1", String.class);
    }
}
