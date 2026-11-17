package cn.edu.sar.domain.access;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.domain.access.AccessModels.*;
import cn.edu.sar.persistence.access.AccessRepository;
import java.time.Clock;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.edu.sar.persistence.access.AccessRepository.at;

@Service
public class AccessService {
    public static final Set<String> MANAGEMENT_ROLES=Set.of("SYSTEM_ADMIN","RESCUE_ADMIN","ADOPTION_ADMIN");
    private final AccessRepository repo;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final List<ResponsibilityProvider> providers;
    public AccessService(AccessRepository repo,PasswordEncoder passwords,Clock clock,List<ResponsibilityProvider> providers) {
        this.repo=repo;this.passwords=passwords;this.clock=clock;this.providers=providers;
        var names=new HashSet<String>();
        for (var provider:providers) if (!Set.of("TASK","CARE","FOLLOWUP").contains(provider.module()) || !names.add(provider.module())) throw new IllegalStateException("Invalid responsibility provider registration");
    }
    public void requireRole(long actor,String role) {
        Account user=repo.find(actor,false);
        if (user==null || !user.active()) throw SessionService.unauthorized();
        if (!repo.roles(actor).contains(role)) throw new BusinessException(HttpStatus.FORBIDDEN,"FORBIDDEN","当前角色没有此操作权限");
    }
    public Account requireAccount(long id,boolean lock) {
        Account user=repo.find(id,lock);
        if (user==null) throw new BusinessException(HttpStatus.NOT_FOUND,"ACCOUNT_NOT_FOUND","账户不存在");
        return user;
    }
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Account lockActiveVolunteer(long id) {
        Account user=requireAccount(id,true);
        if(!user.active() || !user.volunteerState().equals("ACTIVE")) throw new BusinessException(HttpStatus.FORBIDDEN,"VOLUNTEER_REQUIRED","当前没有有效志愿者资格");
        return user;
    }
    private void version(Account user,long expected) {
        if (user.version()!=expected) throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","资料已更新，请刷新后重试");
    }
    private String nickname(String value) {
        String cleaned=value.strip();
        if(cleaned.length()<2 || cleaned.length()>20) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_NICKNAME","昵称去除首尾空白后应为2至20字");
        return cleaned;
    }
    @Transactional
    public User register(String account,String password,String nickname) {
        try { long id=repo.insert(SessionService.normalize(account),passwords.encode(password),nickname(nickname),clock.instant());return repo.user(repo.find(id,false)); }
        catch(DuplicateKeyException duplicate) { throw new BusinessException(HttpStatus.CONFLICT,"ACCOUNT_TAKEN","该账号已被使用"); }
    }
    public User me(long id) { return repo.user(requireAccount(id,false)); }
    @Transactional
    public User profile(long actor,String nickname,String contact,long expected) {
        Account user=requireAccount(actor,true);version(user,expected);
        repo.jdbc().update("UPDATE account SET nickname=?,contact=?,version=version+1 WHERE id=?",nickname(nickname),contact.trim(),actor);
        return me(actor);
    }
    @Transactional
    public void password(long actor,String oldPassword,String newPassword,long expected,String requestId) {
        Account user=requireAccount(actor,true);version(user,expected);
        if (!passwords.matches(oldPassword,user.passwordHash())) throw new BusinessException(HttpStatus.BAD_REQUEST,"OLD_PASSWORD_INVALID","原密码不正确");
        repo.jdbc().update("UPDATE account SET password_hash=?,auth_version=auth_version+1,version=version+1 WHERE id=?",passwords.encode(newPassword),actor);
        repo.jdbc().update("DELETE FROM access_session WHERE user_id=?",actor);
        repo.audit(actor,actor,"PASSWORD_CHANGED","","","本人修改密码",requestId,clock.instant());
    }
    public Page<User> accounts(long actor,String keyword,int page,int size,boolean volunteerManagement) {
        requireRole(actor,volunteerManagement?"RESCUE_ADMIN":"SYSTEM_ADMIN");
        var items=repo.search(keyword,(page-1)*size,size).stream().map(a -> {
            User u=repo.user(a);return new User(u.id(),u.account(),u.nickname(),"",u.volunteerState(),u.roles(),u.version());
        }).toList();
        return new Page<>(items,repo.searchCount(keyword),page,size);
    }
    @Transactional
    public User roles(long actor,long target,List<String> roles,long expected,String reason,String requestId) {
        repo.jdbc().queryForList("SELECT id FROM access_admin_guard WHERE id=1 FOR UPDATE");
        requireRole(actor,"SYSTEM_ADMIN");
        Account user=requireAccount(target,true);version(user,expected);
        Set<String> desired=new TreeSet<>(roles);
        if (!MANAGEMENT_ROLES.containsAll(desired)) throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_ROLE","只允许配置系统、救助或领养管理角色");
        List<String> before=repo.roles(target);
        if (before.contains("SYSTEM_ADMIN") && !desired.contains("SYSTEM_ADMIN") && repo.jdbc().queryForObject("SELECT COUNT(*) FROM role_grant WHERE role='SYSTEM_ADMIN'",Long.class)<=1)
            throw new BusinessException(HttpStatus.CONFLICT,"LAST_ADMIN","至少保留一名系统管理员");
        repo.jdbc().update("DELETE FROM role_grant WHERE user_id=?",target);
        for(String role:desired) repo.jdbc().update("INSERT INTO role_grant(user_id,role) VALUES(?,?)",target,role);
        repo.jdbc().update("UPDATE account SET version=version+1 WHERE id=?",target);
        repo.audit(actor,target,"ROLES_CHANGED",before.toString(),desired.toString(),reason,requestId,clock.instant());
        return me(target);
    }
    @Transactional
    public Application apply(long actor,String material,String key,String requestId) {
        Account user=requireAccount(actor,true);
        String hash=SessionService.digest(material);
        var retried=repo.jdbc().queryForList("SELECT id,request_hash FROM volunteer_request WHERE user_id=? AND request_key=?",actor,key);
        if (!retried.isEmpty()) {
            if (!retried.get(0).get("request_hash").equals(hash)) throw new BusinessException(HttpStatus.CONFLICT,"REQUEST_KEY_CONFLICT","同一请求键不能提交不同材料");
            return repo.application(((Number)retried.get(0).get("id")).longValue());
        }
        if (user.volunteerState().equals("ACTIVE")) throw new BusinessException(HttpStatus.CONFLICT,"ALREADY_VOLUNTEER","你已具有有效志愿者资格");
        if (user.volunteerState().equals("SUSPENDED")) throw new BusinessException(HttpStatus.CONFLICT,"QUALIFICATION_SUSPENDED","资格已暂停，请联系救助管理员核查责任及后续安排");
        var pending=repo.jdbc().queryForList("SELECT id FROM volunteer_request WHERE user_id=? AND state='PENDING'",actor);
        if (!pending.isEmpty()) return repo.application(((Number)pending.get(0).get("id")).longValue());
        repo.jdbc().update("INSERT INTO volunteer_request(user_id,material,request_key,request_hash,created_at) VALUES(?,?,?,?,?)",actor,material,key,hash,at(clock.instant()));
        long id=repo.jdbc().queryForObject("SELECT id FROM volunteer_request WHERE user_id=? AND request_key=?",Long.class,actor,key);
        repo.audit(actor,actor,"VOLUNTEER_APPLIED","NONE","PENDING","提交资格申请",requestId,clock.instant());
        return repo.application(id);
    }
    public Page<Application> applications(long actor,boolean review,String state,int page,int size) {
        if (review) requireRole(actor,"RESCUE_ADMIN");
        Long owner=review?null:actor;
        return new Page<>(repo.applications(owner,state,(page-1)*size,size),repo.applicationCount(owner,state),page,size);
    }
    @Transactional
    public Application review(long actor,long id,String decision,String reason,long expected,String requestId) {
        requireRole(actor,"RESCUE_ADMIN");
        Application initial=repo.application(id);
        if (initial==null) throw new BusinessException(HttpStatus.NOT_FOUND,"APPLICATION_NOT_FOUND","申请不存在");
        requireAccount(Long.parseLong(initial.userId()),true);
        repo.jdbc().queryForList("SELECT id FROM volunteer_request WHERE id=? FOR UPDATE",id);
        Application application=repo.lockedApplication(id);
        // An identical resubmission is safe; changed reasons or decision are not silently accepted.
        if (!application.state().equals("PENDING")) {
            if (application.state().equals(decision) && application.reason().equals(reason) && Long.toString(actor).equals(application.reviewerId())) return application;
            throw new BusinessException(HttpStatus.CONFLICT,"APPLICATION_REVIEWED","申请已处理，请刷新查看");
        }
        if (application.version()!=expected) throw new BusinessException(HttpStatus.CONFLICT,"VERSION_CONFLICT","申请已更新，请刷新");
        repo.jdbc().update("UPDATE volunteer_request SET state=?,reason=?,reviewer_id=?,reviewed_at=?,version=version+1 WHERE id=?",decision,reason,actor,at(clock.instant()),id);
        if (decision.equals("APPROVED")) repo.jdbc().update("UPDATE account SET volunteer_state='ACTIVE',version=version+1 WHERE id=?",Long.parseLong(application.userId()));
        repo.audit(actor,Long.parseLong(application.userId()),"VOLUNTEER_REVIEWED","PENDING",decision,reason,requestId,clock.instant());
        return repo.application(id);
    }
    public ResponsibilityReport responsibilities(long actor,long target) {
        requireRole(actor,"RESCUE_ADMIN");requireAccount(target,false);
        return collect(target);
    }
    private ResponsibilityReport collect(long target) {
        List<Responsibility> open=new ArrayList<>();List<String> missing=new ArrayList<>();
        for(String name:List.of("TASK","CARE","FOLLOWUP")) {
            var provider=providers.stream().filter(p->p.module().equals(name)).findFirst();
            if (provider.isEmpty()) missing.add(name);
            else { try { var result=provider.get().findOpen(target);if(result==null) missing.add(name);else open.addAll(result); }
                catch(RuntimeException failure) { missing.add(name); } }
        }
        return new ResponsibilityReport(List.copyOf(open),List.copyOf(missing));
    }
    @Transactional
    public User suspend(long actor,long target,long expected,String reason,String requestId) {
        requireRole(actor,"RESCUE_ADMIN");
        Account user=requireAccount(target,true);version(user,expected);
        if (!user.volunteerState().equals("ACTIVE")) throw new BusinessException(HttpStatus.CONFLICT,"NOT_ACTIVE_VOLUNTEER","当前不是有效志愿者");
        ResponsibilityReport report=collect(target);
        if (!report.unavailableModules().isEmpty()) throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"RESPONSIBILITY_UNAVAILABLE","责任查询模块尚未接通或暂不可用，无法暂停资格",report);
        if (!report.openResponsibilities().isEmpty()) throw new BusinessException(HttpStatus.CONFLICT,"OPEN_RESPONSIBILITIES","请先处理清单中的未结责任",report);
        repo.jdbc().update("UPDATE account SET volunteer_state='SUSPENDED',version=version+1 WHERE id=?",target);
        repo.audit(actor,target,"VOLUNTEER_SUSPENDED","ACTIVE","SUSPENDED",reason,requestId,clock.instant());
        return me(target);
    }
}
