package cn.edu.sar.domain.access;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.domain.access.AccessModels.*;
import cn.edu.sar.persistence.access.AccessRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static cn.edu.sar.persistence.access.AccessRepository.at;

@Service
public class SessionService {
    public static final String COOKIE = "SAR_SESSION";
    private final AccessRepository repo;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final String dummyHash;
    private final SecureRandom random = new SecureRandom();

    public SessionService(AccessRepository repo,PasswordEncoder passwords,Clock clock,PlatformTransactionManager manager) {
        this.repo=repo;this.passwords=passwords;this.clock=clock;
        this.transaction=new TransactionTemplate(manager);
        this.dummyHash=passwords.encode("non-account-dummy-secret");
    }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String normalize(String name) { return name.trim().toLowerCase(Locale.ROOT); }
    private static Instant instant(Object value) {
        if(value instanceof java.time.LocalDateTime local) return local.toInstant(java.time.ZoneOffset.UTC);
        return ((java.sql.Timestamp)value).toInstant();
    }
    public record Login(String token,User user,String error,int status) {}
    public Login login(String account,String password,String source) {
        // Failure counters must commit even when login is denied; throw only after the transaction returns.
        Login result=transaction.execute(status -> {
            Instant now=clock.instant();
            String origin="SOURCE:"+digest(source), name="ACCOUNT:"+digest(normalize(account));
            guard(origin,now);guard(name,now);
            var src=repo.jdbc().queryForMap("SELECT * FROM login_guard WHERE guard_key=?",origin);
            if (now.isBefore(instant(src.get("window_start")).plusSeconds(300))) {
                if (((Number)src.get("attempts")).intValue()>=60) return new Login(null,null,"SOURCE_LIMIT",429);
            } else repo.jdbc().update("UPDATE login_guard SET attempts=0,window_start=? WHERE guard_key=?",at(now),origin);
            repo.jdbc().update("UPDATE login_guard SET attempts=attempts+1 WHERE guard_key=?",origin);
            var guard=repo.jdbc().queryForMap("SELECT * FROM login_guard WHERE guard_key=?",name);
            var until=guard.get("locked_until");
            if (until!=null && now.isBefore(instant(until))) return new Login(null,null,"LOGIN_FAILED",401);
            if (until!=null) repo.jdbc().update("UPDATE login_guard SET attempts=0,locked_until=NULL WHERE guard_key=?",name);
            Account user=repo.byName(normalize(account));
            boolean matched=passwords.matches(password,user==null?dummyHash:user.passwordHash());
            if (!matched || user==null || !user.active()) {
                repo.jdbc().update("UPDATE login_guard SET attempts=attempts+1 WHERE guard_key=?",name);
                int count=repo.jdbc().queryForObject("SELECT attempts FROM login_guard WHERE guard_key=?",Integer.class,name);
                if (count>=5) repo.jdbc().update("UPDATE login_guard SET locked_until=? WHERE guard_key=?",at(now.plusSeconds(900)),name);
                return new Login(null,null,"LOGIN_FAILED",401);
            }
            // Password change and session creation share the account row lock.
            Account locked=repo.find(user.id(),true);
            if (!locked.passwordHash().equals(user.passwordHash()) || !locked.active()) return new Login(null,null,"LOGIN_FAILED",401);
            repo.jdbc().update("UPDATE login_guard SET attempts=0,locked_until=NULL WHERE guard_key=?",name);
            byte[] bytes=new byte[32];random.nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
            repo.jdbc().update("INSERT INTO access_session(token_hash,user_id,auth_version,last_activity,created_at) VALUES(?,?,?,?,?)",digest(token),locked.id(),locked.authVersion(),at(now),at(now));
            return new Login(token,repo.user(locked),null,200);
        });
        if (result.error()!=null) throw new BusinessException(HttpStatus.valueOf(result.status()),result.error(),
            result.status()==429?"登录尝试过于频繁，请在5分钟窗口结束后重试":"账号或密码错误，或登录暂受限制，请稍后重试");
        return result;
    }
    private void guard(String key,Instant now) {
        repo.jdbc().update("INSERT IGNORE INTO login_guard(guard_key,window_start) VALUES(?,?)",key,at(now));
        repo.jdbc().queryForList("SELECT guard_key FROM login_guard WHERE guard_key=? FOR UPDATE",key);
    }
    @Transactional
    public Account authenticate(String token) {
        if (token==null || !token.matches("[a-f0-9]{64}")) throw unauthorized();
        var sessions=repo.jdbc().queryForList("SELECT user_id,auth_version,last_activity FROM access_session WHERE token_hash=?",digest(token));
        if (sessions.isEmpty()) throw unauthorized();
        var session=sessions.get(0);
        Account user=repo.find(((Number)session.get("user_id")).longValue(),false);
        Instant now=clock.instant(),last=instant(session.get("last_activity"));
        if (user==null || !user.active() || user.authVersion()!=((Number)session.get("auth_version")).longValue()
            || Duration.between(last,now).compareTo(Duration.ofMinutes(30))>=0) throw unauthorized();
        // Conditional update stops two parallel requests from reviving an expired/deleted session.
        int touched=repo.jdbc().update("UPDATE access_session SET last_activity=GREATEST(last_activity,?) WHERE token_hash=? AND last_activity>?",at(now),digest(token),at(now.minusSeconds(1800)));
        if (touched!=1) throw unauthorized();
        return user;
    }
    public void logout(String token) { if (token!=null) repo.jdbc().update("DELETE FROM access_session WHERE token_hash=?",digest(token)); }
    public static BusinessException unauthorized() { return new BusinessException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","请先登录或重新登录"); }
}
