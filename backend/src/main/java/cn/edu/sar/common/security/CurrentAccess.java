package cn.edu.sar.common.security;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.domain.access.SessionService;
import cn.edu.sar.persistence.access.AccessRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The ID must come from AccessInterceptor.current(request), never from client actorId fields. */
@Service
public class CurrentAccess {
    public record Actor(long id, Set<String> roles, String volunteerState, long version) {
        public Actor { roles = Set.copyOf(roles); }
    }
    private final AccessRepository repo;
    public CurrentAccess(AccessRepository repo) { this.repo = repo; }
    public Actor load(long authenticatedId) {
        boolean transaction = TransactionSynchronizationManager.isActualTransactionActive()
            && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        var user = repo.find(authenticatedId, transaction);
        if (user == null || !user.active()) throw SessionService.unauthorized();
        var roles = new HashSet<>(transaction
            ? repo.jdbc().queryForList("SELECT role FROM role_grant WHERE user_id=? FOR SHARE", String.class, authenticatedId)
            : repo.roles(authenticatedId));
        roles.add("USER");
        if (user.volunteerState().equals("ACTIVE")) roles.add("VOLUNTEER");
        return new Actor(user.id(), roles, user.volunteerState(), user.version());
    }
    public Actor requireRole(long authenticatedId, String role) {
        Actor actor = load(authenticatedId);
        if (!actor.roles().contains(role)) throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "当前角色没有此操作权限");
        return actor;
    }
}
