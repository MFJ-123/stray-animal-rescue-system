package cn.edu.sar.domain.access;

import cn.edu.sar.common.security.*;
import org.springframework.stereotype.Component;

@Component
public class AccountPolicy implements ObjectPolicy {
    public String resourceType() { return "ACCOUNT"; }
    public Decision decide(CurrentAccess.Actor actor, String objectId, String action) {
        if (!Long.toString(actor.id()).equals(objectId)) return Decision.deny();
        return switch (action) {
            case "READ" -> Decision.allow("id", "account", "nickname", "contact", "volunteerState", "roles", "version");
            case "UPDATE_PROFILE" -> Decision.allow();
            default -> Decision.deny();
        };
    }
}
