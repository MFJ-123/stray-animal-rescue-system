package cn.edu.sar.common.security;

import java.util.Set;

/** Each resource has exactly one provider; use real persisted ownership facts. */
public interface ObjectPolicy {
    String resourceType();
    Decision decide(CurrentAccess.Actor actor, String objectId, String action);
    record Decision(boolean allowed, Set<String> readableFields) {
        public Decision { readableFields = Set.copyOf(readableFields); }
        public static Decision deny() { return new Decision(false, Set.of()); }
        public static Decision allow(String... fields) { return new Decision(true, Set.of(fields)); }
    }
}
