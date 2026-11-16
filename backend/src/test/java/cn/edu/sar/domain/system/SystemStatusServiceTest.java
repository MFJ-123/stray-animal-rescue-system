package cn.edu.sar.domain.system;

import cn.edu.sar.persistence.system.SystemProbeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemStatusServiceTest {
    @Test
    void readsDatabaseAndUsesServerClock() {
        var repository = mock(SystemProbeRepository.class);
        when(repository.databaseVersion()).thenReturn("8.0.46");
        when(repository.foundationVersion()).thenReturn("0.1.0");
        var instant = Instant.parse("2026-11-16T00:00:00Z");
        var service = new SystemStatusService(repository, Clock.fixed(instant, ZoneOffset.UTC));
        var status = service.status();
        assertThat(status.database()).isEqualTo("CONNECTED");
        assertThat(status.databaseVersion()).isEqualTo("8.0.46");
        assertThat(status.checkedAt()).isEqualTo(instant);
    }

    @Test
    void doesNotReportSuccessWhenDatabaseFails() {
        var repository = mock(SystemProbeRepository.class);
        when(repository.databaseVersion()).thenThrow(new DataAccessResourceFailureException("private connection details"));
        var service = new SystemStatusService(repository, Clock.systemUTC());
        assertThatThrownBy(service::status).isInstanceOf(DataAccessResourceFailureException.class);
    }
}
