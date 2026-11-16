package cn.edu.sar.domain.system;

import cn.edu.sar.persistence.system.SystemProbeRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {
    private final SystemProbeRepository repository;
    private final Clock clock;

    public SystemStatusService(SystemProbeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Status status() {
        return new Status("UP", "CONNECTED", repository.databaseVersion(), repository.foundationVersion(), clock.instant());
    }

    public record Status(String service, String database, String databaseVersion, String foundationVersion, Instant checkedAt) {}
}
