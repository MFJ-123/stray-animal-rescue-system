package cn.edu.sar;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.domain.access.*;
import cn.edu.sar.domain.access.AccessModels.*;
import cn.edu.sar.persistence.access.AccessRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccessIT {
    @Autowired AccessService access;
    @Autowired SessionService sessions;
    @Autowired AccessRepository repo;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired TestClock clock;
    @Autowired Providers providers;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    static final String PASSWORD="FixtureOnly123";
    long admin;
    @BeforeEach void setup() {
        clock.now.set(Instant.parse("2026-11-17T00:00:00Z")); providers.mode="CLEAR";
        Account account=repo.byName("accessitadmin");
        if(account==null) { admin=Long.parseLong(access.register("accessitadmin",PASSWORD,"测试管理员").id()); }
        else admin=account.id();
        for(String role:AccessService.MANAGEMENT_ROLES) repo.jdbc().update("INSERT IGNORE INTO role_grant(user_id,role) VALUES(?,?)",admin,role);
    }
    User user() { return access.register("it"+UUID.randomUUID().toString().replace("-","").substring(0,16),PASSWORD,"测试用户"); }
    String login(User user) { return sessions.login(user.account(),PASSWORD,"fixture-"+UUID.randomUUID()).token(); }
    long id(User user) { return Long.parseLong(user.id()); }
    @Test void registrationNormalizesAndStoresSaltedHashes() {
        String name="Ab"+UUID.randomUUID().toString().replace("-","").substring(0,12);
        User first=access.register(name,PASSWORD,"用户一"),second=user();
        assertThat(first.account()).isEqualTo(name.toLowerCase(Locale.ROOT));
        assertThatThrownBy(()->access.register(name.toUpperCase(Locale.ROOT),PASSWORD,"重复用户")).isInstanceOf(BusinessException.class);
        assertThat(repo.find(id(first),false).passwordHash()).isNotEqualTo(PASSWORD).isNotEqualTo(repo.find(id(second),false).passwordHash());
        assertThat(first.roles()).containsExactly("USER");
    }
    @Test void profileVersionAndPasswordRevokeAllSessions() {
        User user=user();String token1=login(user),token2=login(user);
        User changed=access.profile(id(user),"修改昵称","123456789",user.version());
        assertThat(changed.contact()).isEqualTo("123456789");
        assertThatThrownBy(()->access.profile(id(user),"旧版本名","",user.version())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->access.password(id(user),"Wrong123","Changed123",changed.version(),"test")).isInstanceOf(BusinessException.class);
        assertThat(sessions.authenticate(token1).id()).isEqualTo(id(user));
        access.password(id(user),PASSWORD,"Changed123",changed.version(),"test");
        assertThatThrownBy(()->sessions.authenticate(token1)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sessions.authenticate(token2)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sessions.login(user.account(),PASSWORD,"old-password-source")).isInstanceOf(BusinessException.class);
        assertThat(sessions.login(user.account(),"Changed123","new-password-source").user().id()).isEqualTo(user.id());
    }
    @Test void lockoutAndSourceWindowUseCommittedCounters() {
        User user=user();String source="lock-source-"+user.id();
        for(int i=0;i<5;i++) assertThatThrownBy(()->sessions.login(user.account(),"Wrong123",source)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->sessions.login(user.account(),PASSWORD,source)).isInstanceOf(BusinessException.class);
        clock.now.set(clock.instant().plusSeconds(901));
        assertThat(sessions.login(user.account(),PASSWORD,source).user().id()).isEqualTo(user.id());
        String frequent="rate-source-"+user.id();
        for(int i=0;i<60;i++) { try { sessions.login("missing"+i,"Wrong123",frequent); } catch(BusinessException expected) { assertThat(expected.code()).isEqualTo("LOGIN_FAILED"); } }
        assertThatThrownBy(()->sessions.login(user.account(),PASSWORD,frequent)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("SOURCE_LIMIT"));
        clock.now.set(clock.instant().plusSeconds(301));
        assertThat(sessions.login(user.account(),PASSWORD,frequent).user().id()).isEqualTo(user.id());
    }
    @Test void logoutAndThirtyMinuteIdleInvalidateTokens() {
        User user=user();String token=login(user);sessions.logout(token);
        assertThatThrownBy(()->sessions.authenticate(token)).isInstanceOf(BusinessException.class);
        String idle=login(user);clock.now.set(clock.instant().plusSeconds(1800));
        assertThatThrownBy(()->sessions.authenticate(idle)).isInstanceOf(BusinessException.class);
    }
    @Test void duplicateApplyAndReviewPreserveSingleFact() throws Exception {
        User user=user();String material="我希望参与校园流浪动物救助工作";
        Application first=access.apply(id(user),material,"apply-test-01","request");
        assertThat(access.apply(id(user),material,"apply-test-01","retry").id()).isEqualTo(first.id());
        assertThat(access.apply(id(user),material,"another-key-02","repeat").id()).isEqualTo(first.id());
        assertThatThrownBy(()->access.apply(id(user),material+"变更","apply-test-01","wrong")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->access.review(id(user),Long.parseLong(first.id()),"APPROVED","材料完整符合资格申请要求",0,"deny")).isInstanceOf(BusinessException.class);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            List<Callable<Application>> jobs=List.of(()->access.review(admin,Long.parseLong(first.id()),"APPROVED","材料完整符合资格申请要求",0,"r1"),()->access.review(admin,Long.parseLong(first.id()),"APPROVED","材料完整符合资格申请要求",0,"r2"));
            for(Future<Application> result:executor.invokeAll(jobs)) assertThat(result.get().state()).isEqualTo("APPROVED");
        } finally { executor.shutdownNow(); }
        assertThat(access.me(id(user)).volunteerState()).isEqualTo("ACTIVE");
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_log WHERE object_id=? AND action='VOLUNTEER_REVIEWED'",Long.class,id(user))).isEqualTo(1L);
        assertThat(access.applications(id(user),false,"",1,20).total()).isEqualTo(1);
        User other=user();
        assertThat(access.applications(id(other),false,"",1,20).total()).isZero();
    }
    @Test void roleChangesDoNotDependOnVolunteerResponsibilitiesAndAreImmediate() {
        User user=user();String token=login(user);providers.mode="OPEN";
        access.roles(admin,id(user),List.of("RESCUE_ADMIN"),user.version(),"授予救助管理角色用于测试","r");
        assertThat(sessions.authenticate(token).id()).isEqualTo(id(user));access.requireRole(id(user),"RESCUE_ADMIN");
        User latest=access.me(id(user));
        access.roles(admin,id(user),List.of(),latest.version(),"撤销救助管理角色用于测试","r");
        assertThatThrownBy(()->access.requireRole(id(user),"RESCUE_ADMIN")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->access.roles(id(user),admin,List.of(),0,"普通用户不能配置管理角色","r")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->access.roles(admin,admin,List.of("RESCUE_ADMIN"),access.me(admin).version(),"不能撤销最后系统管理员","r")).isInstanceOf(BusinessException.class);
    }
    @Test void suspensionFailsClosedUntilProvidersAndResponsibilitiesAreClear() {
        User user=user();Application application=access.apply(id(user),"我愿意参与救助并遵守工作安排","suspend-apply-01","r");
        access.review(admin,Long.parseLong(application.id()),"APPROVED","材料完整审核通过授予资格",0,"r");
        long version=access.me(id(user)).version();
        providers.mode="UNAVAILABLE";
        assertThatThrownBy(()->access.suspend(admin,id(user),version,"责任模块暂不可用不能暂停","r")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        providers.mode="OPEN";
        assertThatThrownBy(()->access.suspend(admin,id(user),version,"未处理完任务责任不能暂停","r")).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("OPEN_RESPONSIBILITIES"));
        assertThat(access.me(id(user)).volunteerState()).isEqualTo("ACTIVE");
        providers.mode="CLEAR";
        assertThat(access.suspend(admin,id(user),version,"所有责任交接完成暂停资格","r").volunteerState()).isEqualTo("SUSPENDED");
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThatThrownBy(()->transaction.execute(status->access.lockActiveVolunteer(id(user)))).isInstanceOf(BusinessException.class);
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_log WHERE object_id=? AND action='VOLUNTEER_SUSPENDED'",Long.class,id(user))).isEqualTo(1);
    }
    @Test void roleChangeRollsBackWhenFinalAuditWriteFails() {
        User user=user();
        assertThatThrownBy(()->access.roles(admin,id(user),List.of("RESCUE_ADMIN"),user.version(),"x".repeat(501),"rollback-test"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(access.me(id(user)).roles()).containsExactly("USER");
        assertThat(access.me(id(user)).version()).isEqualTo(user.version());
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_log WHERE request_id='rollback-test' AND object_id=?",Long.class,id(user))).isZero();
    }
    @Test void httpRequiresCsrfAndEnforcesObjectAndRoleBoundaries() throws Exception {
        var missingCsrf=http.postForEntity("/api/v1/accounts",Map.of("account","validuser","password",PASSWORD,"nickname","测试用户"),String.class);
        assertThat(missingCsrf.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(missingCsrf.getBody()).contains("CSRF_INVALID","requestId");
        var me=http.getForEntity("/api/v1/accounts/me",String.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        User user=user();String token=login(user);
        HttpHeaders headers=new HttpHeaders();headers.add(HttpHeaders.COOKIE,"SAR_SESSION="+token);
        var response=http.exchange("/api/v1/accounts",HttpMethod.GET,new HttpEntity<>(headers),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var csrf=http.getForEntity("/api/v1/csrf",String.class);JsonNode body=json.readTree(csrf.getBody());
        headers.add(HttpHeaders.COOKIE,csrf.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0]);
        headers.add("X-XSRF-TOKEN",body.path("data").path("token").asText());
        var registered=http.exchange("/api/v1/accounts",HttpMethod.POST,new HttpEntity<>(Map.of("account","http"+UUID.randomUUID().toString().replace("-","").substring(0,12),"password",PASSWORD,"nickname","网络测试"),headers),String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registered.getBody()).doesNotContain("passwordHash",PASSWORD);
    }
    static class TestClock extends Clock {
        AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-11-17T00:00:00Z"));
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return now.get();}
    }
    static class Providers { volatile String mode="CLEAR"; }
    @TestConfiguration static class Fixtures {
        @Bean @Primary TestClock testClock(){return new TestClock();}
        @Bean Providers providers(){return new Providers();}
        @Bean ResponsibilityProvider taskProvider(Providers p){return provider("TASK",p);}
        @Bean ResponsibilityProvider careProvider(Providers p){return provider("CARE",p);}
        @Bean ResponsibilityProvider followupProvider(Providers p){return provider("FOLLOWUP",p);}
        ResponsibilityProvider provider(String name,Providers p){return new ResponsibilityProvider(){
            public String module(){return name;}
            public List<Responsibility> findOpen(long id){if(p.mode.equals("UNAVAILABLE"))throw new IllegalStateException("fixture failure");return p.mode.equals("OPEN")?List.of(new Responsibility(name,"123","未结责任","/")):List.of();}
        };}
    }
}
