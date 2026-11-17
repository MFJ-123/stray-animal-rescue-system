package cn.edu.sar.persistence.access;

import cn.edu.sar.domain.access.AccessModels.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AccessRepository {
    private final JdbcTemplate jdbc;
    public AccessRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public JdbcTemplate jdbc() { return jdbc; }
    public static java.time.LocalDateTime at(Instant value) { return java.time.LocalDateTime.ofInstant(value,java.time.ZoneOffset.UTC); }
    private final RowMapper<Account> accountMapper = (r,n) -> new Account(r.getLong("id"),r.getString("account"),
        r.getString("password_hash"),r.getString("nickname"),r.getString("contact"),r.getString("volunteer_state"),
        r.getBoolean("active"),r.getLong("auth_version"),r.getLong("version"));
    public Account find(long id, boolean lock) {
        return jdbc.query("SELECT * FROM account WHERE id=?"+(lock?" FOR UPDATE":""),accountMapper,id).stream().findFirst().orElse(null);
    }
    public Account byName(String name) { return jdbc.query("SELECT * FROM account WHERE account=?",accountMapper,name).stream().findFirst().orElse(null); }
    public List<String> roles(long id) { return jdbc.queryForList("SELECT role FROM role_grant WHERE user_id=? ORDER BY role",String.class,id); }
    public User user(Account a) {
        var roles = new java.util.ArrayList<>(roles(a.id()));
        roles.add(0,"USER");
        if (a.volunteerState().equals("ACTIVE")) roles.add("VOLUNTEER");
        return new User(Long.toString(a.id()),a.account(),a.nickname(),a.contact(),a.volunteerState(),roles,a.version());
    }
    public List<Account> search(String keyword, int offset, int limit) {
        return jdbc.query("SELECT * FROM account WHERE account LIKE ? OR nickname LIKE ? ORDER BY id DESC LIMIT ? OFFSET ?",accountMapper,
            "%"+keyword+"%","%"+keyword+"%",limit,offset);
    }
    public long searchCount(String keyword) { return jdbc.queryForObject("SELECT COUNT(*) FROM account WHERE account LIKE ? OR nickname LIKE ?",Long.class,"%"+keyword+"%","%"+keyword+"%"); }
    public long insert(String account,String hash,String nickname,Instant now) {
        jdbc.update("INSERT INTO account(account,password_hash,nickname,created_at) VALUES(?,?,?,?)",account,hash,nickname,at(now));
        return byName(account).id();
    }
    public Application application(long id) { return jdbc.query("SELECT v.*,a.nickname FROM volunteer_request v JOIN account a ON a.id=v.user_id WHERE v.id=?",applicationMapper,id).stream().findFirst().orElse(null); }
    public Application lockedApplication(long id) { return jdbc.query("SELECT v.*,a.nickname FROM volunteer_request v JOIN account a ON a.id=v.user_id WHERE v.id=? FOR UPDATE",applicationMapper,id).stream().findFirst().orElse(null); }
    private final RowMapper<Application> applicationMapper = (r,n) -> new Application(r.getString("id"),r.getString("user_id"),r.getString("nickname"),r.getString("material"),r.getString("state"),r.getString("reason"),r.getString("reviewer_id"),r.getLong("version"),r.getObject("created_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),r.getObject("reviewed_at")==null?null:r.getObject("reviewed_at",java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC));
    public List<Application> applications(Long owner,String state,int offset,int limit) {
        String where=owner==null?" WHERE 1=1":" WHERE v.user_id="+owner;
        if (!state.isEmpty()) where+=" AND v.state=?";
        return state.isEmpty()?jdbc.query("SELECT v.*,a.nickname FROM volunteer_request v JOIN account a ON a.id=v.user_id"+where+" ORDER BY v.id DESC LIMIT ? OFFSET ?",applicationMapper,limit,offset):jdbc.query("SELECT v.*,a.nickname FROM volunteer_request v JOIN account a ON a.id=v.user_id"+where+" ORDER BY v.id DESC LIMIT ? OFFSET ?",applicationMapper,state,limit,offset);
    }
    public long applicationCount(Long owner,String state) {
        String where=owner==null?" WHERE 1=1":" WHERE user_id="+owner;
        return state.isEmpty()?jdbc.queryForObject("SELECT COUNT(*) FROM volunteer_request"+where,Long.class):jdbc.queryForObject("SELECT COUNT(*) FROM volunteer_request"+where+" AND state=?",Long.class,state);
    }
    public void audit(long actor,long target,String action,String before,String after,String reason,String requestId,Instant now) {
        jdbc.update("INSERT INTO audit_log(actor_id,object_type,object_id,action,before_state,after_state,reason,request_id,created_at) VALUES(?,'ACCOUNT',?,?,?,?,?,?,?)",actor,target,action,before,after,reason,requestId,at(now));
    }
}
