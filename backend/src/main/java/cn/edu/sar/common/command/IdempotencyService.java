package cn.edu.sar.common.command;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.common.security.CurrentAccess;
import cn.edu.sar.domain.access.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static cn.edu.sar.persistence.access.AccessRepository.at;

/** A successful receipt and business writes share the REQUIRED transaction; errors persist neither. */
@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final CurrentAccess access;
    private final TransactionTemplate transaction;
    public IdempotencyService(JdbcTemplate jdbc, ObjectMapper json, Clock clock, PlatformTransactionManager manager, CurrentAccess access) {
        this.jdbc = jdbc; this.json = json; this.clock = clock; this.transaction = new TransactionTemplate(manager); this.access = access;
    }
    public <T> T execute(long actor, String action, String key, Object payload, Class<T> resultType,
                         Runnable authorizeEveryTime, Supplier<T> work) {
        if (actor <= 0 || action == null || !action.matches("[A-Z0-9_.:-]{1,80}") || key == null || !key.matches("[A-Za-z0-9_-]{8,64}"))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_KEY", "请求键或操作标识无效");
        Objects.requireNonNull(authorizeEveryTime); Objects.requireNonNull(work); Objects.requireNonNull(resultType);
        String hash = SessionService.digest(write(canonical(json.valueToTree(payload))));
        return transaction.execute(status -> {
            // Lock actor BEFORE the receipt FK acquires shared locks: concurrent commands must not
            // upgrade FK locks while waiting for each other's receipt lock. Same-actor writes serialize.
            access.load(actor);
            // Upsert obtains the unique-record lock even when another request has not committed yet.
            jdbc.update("INSERT INTO idempotency_receipt(actor_id,action,request_key,payload_hash,created_at) VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE request_key=request_key",
                actor, action, key, hash, at(clock.instant()));
            var receipt = jdbc.queryForMap("SELECT payload_hash,result_json FROM idempotency_receipt WHERE actor_id=? AND action=? AND request_key=? FOR UPDATE", actor, action, key);
            // Revoked roles/changed ownership must also deny an otherwise valid replay.
            authorizeEveryTime.run();
            if (!hash.equals(receipt.get("payload_hash"))) throw new BusinessException(HttpStatus.CONFLICT, "REQUEST_KEY_CONFLICT", "同一请求键不能提交不同内容");
            if (receipt.get("result_json") != null) {
                try { return json.readValue(receipt.get("result_json").toString(), resultType); }
                catch (JsonProcessingException invalid) { throw new IllegalStateException("Stored receipt schema mismatch", invalid); }
            }
            T result = Objects.requireNonNull(work.get(), "Idempotent commands must return a public result DTO");
            jdbc.update("UPDATE idempotency_receipt SET result_json=? WHERE actor_id=? AND action=? AND request_key=?", write(result), actor, action, key);
            return result;
        });
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException invalid) { throw new IllegalArgumentException("Command must be JSON serializable", invalid); }
    }
    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = json.createObjectNode();
            var names = new TreeSet<String>(); value.fieldNames().forEachRemaining(names::add);
            for (String name : names) result.set(name, canonical(value.get(name)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = json.createArrayNode(); value.forEach(item -> result.add(canonical(item))); return result;
        }
        return value;
    }
}
