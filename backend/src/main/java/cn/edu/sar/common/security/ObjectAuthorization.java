package cn.edu.sar.common.security;

import cn.edu.sar.common.api.BusinessException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ObjectAuthorization {
    private final CurrentAccess access;
    private final Map<String, ObjectPolicy> policies = new HashMap<>();
    public ObjectAuthorization(CurrentAccess access, List<ObjectPolicy> providers) {
        this.access = access;
        for (ObjectPolicy provider : providers) {
            if (provider.resourceType() == null || policies.putIfAbsent(provider.resourceType(), provider) != null)
                throw new IllegalStateException("Duplicate or invalid object policy");
        }
    }
    public ObjectPolicy.Decision require(long actor, String resourceType, String objectId, String action) {
        var current = access.load(actor);
        return decide(current, resourceType, objectId, action);
    }
    private ObjectPolicy.Decision decide(CurrentAccess.Actor current, String resourceType, String objectId, String action) {
        var policy = policies.get(resourceType);
        if (policy == null) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "AUTHORIZATION_UNAVAILABLE", "该业务的对象权限尚未接入");
        var decision = policy.decide(current, objectId, action);
        if (decision == null || !decision.allowed()) throw new BusinessException(HttpStatus.FORBIDDEN, "OBJECT_FORBIDDEN", "无权操作此对象");
        return decision;
    }
    /** Explicit flat public DTO only. Nested objects require a separately authorized DTO, never entity serialization. */
    public Map<String, Object> project(long actor, String type, String id, Map<String, ?> dto) {
        return filter(require(actor, type, id, "READ"), dto);
    }
    public Map<String, Object> projectPublic(String type, String id, Map<String, ?> dto) {
        var guest = new CurrentAccess.Actor(0, Set.of("ANONYMOUS"), "NONE", 0);
        return filter(decide(guest, type, id, "READ_PUBLIC"), dto);
    }
    private Map<String, Object> filter(ObjectPolicy.Decision decision, Map<String, ?> dto) {
        var result = new LinkedHashMap<String, Object>();
        for (String field : decision.readableFields()) {
            if (!dto.containsKey(field)) continue;
            Object value = dto.get(field);
            if (!scalar(value) && !(value instanceof Collection<?> items && items.stream().allMatch(ObjectAuthorization::scalar)))
                throw new IllegalArgumentException("Public DTO fields must be scalar or scalar collections: " + field);
            result.put(field, value instanceof Collection<?> items ? new ArrayList<>(items) : value);
        }
        return Collections.unmodifiableMap(result);
    }
    private static boolean scalar(Object value) {
        return value == null || value instanceof String || value instanceof Number || value instanceof Boolean;
    }
}
