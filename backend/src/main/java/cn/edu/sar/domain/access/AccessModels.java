package cn.edu.sar.domain.access;

import java.time.Instant;
import java.util.List;

public final class AccessModels {
    private AccessModels() {}
    public record Account(long id, String account, String passwordHash, String nickname, String contact,
                          String volunteerState, boolean active, long authVersion, long version) {}
    public record User(String id, String account, String nickname, String contact,
                       String volunteerState, List<String> roles, long version) {}
    public record Application(String id, String userId, String nickname, String material, String state,
                              String reason, String reviewerId, long version, Instant createdAt, Instant reviewedAt) {}
    public record Responsibility(String type, String objectId, String description, String path) {}
    public record ResponsibilityReport(List<Responsibility> openResponsibilities, List<String> unavailableModules) {}
    public record Page<T>(List<T> items, long total, int page, int pageSize) {}
}
