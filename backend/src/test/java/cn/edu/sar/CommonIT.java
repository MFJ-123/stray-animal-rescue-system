package cn.edu.sar;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.common.command.IdempotencyService;
import cn.edu.sar.common.security.*;
import cn.edu.sar.common.web.PublicRead;
import cn.edu.sar.common.api.ApiResponse;
import cn.edu.sar.domain.access.*;
import cn.edu.sar.domain.access.AccessModels.User;
import cn.edu.sar.persistence.access.AccessRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommonIT {
    @Autowired IdempotencyService commands;
    @Autowired AccessService access;
    @Autowired AccessRepository repo;
    @Autowired CurrentAccess current;
    @Autowired ObjectAuthorization objects;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SessionService sessions;
    @Autowired ObjectMapper json;
    @Autowired TestRestTemplate http;
    @Autowired FixturePolicy policy;
    public record Result(String id, int count) {}
    User user() { return access.register("cm"+UUID.randomUUID().toString().replace("-", "").substring(0,16), "FixtureOnly123", "公共测试"); }
    long id(User user) { return Long.parseLong(user.id()); }
    String key() { return UUID.randomUUID().toString(); }
    long receipts(long actor) { return repo.jdbc().queryForObject("SELECT COUNT(*) FROM idempotency_receipt WHERE actor_id=?", Long.class, actor); }
    Runnable authenticated(long actor) { return () -> current.load(actor); }

    @Test void canonicalPayloadReplaysAndChangedPayloadConflicts() {
        long actor=id(user()); String key=key(); var calls=new AtomicInteger();
        var a=new LinkedHashMap<String,Object>(); a.put("a",1); a.put("b",Map.of("x",2,"y",3));
        var b=new LinkedHashMap<String,Object>(); b.put("b",Map.of("y",3,"x",2)); b.put("a",1);
        Result first=commands.execute(actor,"FIXTURE.V1",key,a,Result.class,authenticated(actor),()->new Result("one",calls.incrementAndGet()));
        Result replay=commands.execute(actor,"FIXTURE.V1",key,b,Result.class,authenticated(actor),()->{throw new AssertionError("reexecuted");});
        assertThat(replay).isEqualTo(first); assertThat(calls).hasValue(1); assertThat(receipts(actor)).isEqualTo(1);
        assertThatThrownBy(()->commands.execute(actor,"FIXTURE.V1",key,Map.of("a",2),Result.class,authenticated(actor),()->first))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("REQUEST_KEY_CONFLICT"));
    }
    @Test void eightConcurrentSubmissionsRunBusinessOnce() throws Exception {
        long actor=id(user()); String key=key(); var calls=new AtomicInteger(); var start=new CountDownLatch(1);
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<Result>> futures=new ArrayList<>();
            for(int i=0;i<8;i++) futures.add(pool.submit(()->{
                start.await(); return commands.execute(actor,"FIXTURE.V1",key,Map.of("n",1),Result.class,authenticated(actor),()->new Result("same",calls.incrementAndGet()));
            }));
            start.countDown();
            for(var future:futures) assertThat(future.get(20,TimeUnit.SECONDS)).isEqualTo(new Result("same",1));
        } finally { pool.shutdownNow(); }
        assertThat(calls).hasValue(1); assertThat(receipts(actor)).isEqualTo(1);
    }
    @Test void failedBusinessRollsBackChangesAndReceiptAndCanRetry() {
        User user=user(); long actor=id(user); String key=key();
        assertThatThrownBy(()->commands.execute(actor,"FIXTURE.V1",key,Map.of(),User.class,authenticated(actor),()->{
            access.profile(actor,"回滚昵称","private",user.version()); throw new IllegalStateException("simulated failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(access.me(actor).version()).isEqualTo(user.version()); assertThat(receipts(actor)).isZero();
        var result=commands.execute(actor,"FIXTURE.V1",key,Map.of(),User.class,authenticated(actor),()->access.profile(actor,"成功昵称","",user.version()));
        assertThat(result.version()).isEqualTo(user.version()+1); assertThat(receipts(actor)).isEqualTo(1);
    }
    @Test void participatesInOuterRollbackAndScopesKeysByActorAndAction() {
        long one=id(user()),two=id(user()); String key=key();
        new TransactionTemplate(transactions).execute(status->{
            commands.execute(one,"FIXTURE.V1",key,Map.of(),Result.class,authenticated(one),()->new Result("rolled-back",1));
            status.setRollbackOnly(); return null;
        });
        assertThat(receipts(one)).isZero();
        for(long actor:List.of(one,two)) for(String action:List.of("FIXTURE.V1","FIXTURE.OTHER.V1"))
            commands.execute(actor,action,key,Map.of(),Result.class,authenticated(actor),()->new Result("ok",1));
        assertThat(receipts(one)).isEqualTo(2); assertThat(receipts(two)).isEqualTo(2);
    }
    @Test void revokedRolesAndDisabledAccountsCannotReplay() {
        long actor=id(user()); String key=key();
        repo.jdbc().update("INSERT INTO role_grant(user_id,role) VALUES(?,'RESCUE_ADMIN')",actor);
        Runnable authorize=()->current.requireRole(actor,"RESCUE_ADMIN");
        commands.execute(actor,"FIXTURE.V1",key,Map.of(),Result.class,authorize,()->new Result("protected",1));
        repo.jdbc().update("DELETE FROM role_grant WHERE user_id=?",actor);
        assertThatThrownBy(()->commands.execute(actor,"FIXTURE.V1",key,Map.of(),Result.class,authorize,()->new Result("wrong",2)))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("FORBIDDEN"));
        repo.jdbc().update("UPDATE account SET active=FALSE WHERE id=?",actor);
        assertThatThrownBy(()->commands.execute(actor,"FIXTURE.V1",key,Map.of(),Result.class,authenticated(actor),()->new Result("wrong",2)))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status().value()).isEqualTo(401));
    }
    @Test void ownershipIsRecheckedBeforeReplayAndFieldsUseAllowList() {
        long actor=id(user()),other=id(user()); String object=key(),key=key(); policy.owners.put(object,actor);
        Runnable authorize=()->objects.require(actor,"FIXTURE_OBJECT",object,"WRITE");
        commands.execute(actor,"FIXTURE.V1",key,Map.of(),Result.class,authorize,()->new Result(object,1));
        var publicDto=objects.project(actor,"FIXTURE_OBJECT",object,Map.of("id",object,"name","公开字段","contact","private","passwordHash","secret"));
        assertThat(publicDto).containsOnlyKeys("id","name");
        assertThatThrownBy(()->objects.project(actor,"FIXTURE_OBJECT",object,Map.of("name",Map.of("secret","nested")))).isInstanceOf(IllegalArgumentException.class);
        policy.owners.put(object,other);
        assertThatThrownBy(()->commands.execute(actor,"FIXTURE.V1",key,Map.of(),Result.class,authorize,()->new Result("wrong",2)))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("OBJECT_FORBIDDEN"));
    }
    @Test void unknownPoliciesActionsAndOtherAccountsAreDenied() {
        long actor=id(user());
        var readOnly=new TransactionTemplate(transactions); readOnly.setReadOnly(true);
        Long readActor=readOnly.execute(status->current.load(actor).id());
        assertThat(readActor).isEqualTo(actor);
        assertThatThrownBy(()->objects.require(actor,"NOT_IMPLEMENTED","1","READ"))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("AUTHORIZATION_UNAVAILABLE"));
        assertThatThrownBy(()->objects.require(actor,"ACCOUNT",Long.toString(actor),"UNKNOWN")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->objects.require(actor,"ACCOUNT",user().id(),"READ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->commands.execute(actor,"FIXTURE.V1","bad",Map.of(),Result.class,authenticated(actor),()->new Result("wrong",1)))
            .isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status().value()).isEqualTo(400));
        assertThat(receipts(actor)).isZero();
        assertThatThrownBy(()->objects.projectPublic("ACCOUNT",Long.toString(actor),Map.of("contact","secret"))).isInstanceOf(BusinessException.class);
    }
    @Test void explicitPublicReadIsAvailableButNeverOpensPrivateReadsOrWrites() throws Exception {
        assertThat(http.getForEntity("/api/v1/common-fixture/public",String.class).getStatusCode().value()).isEqualTo(200);
        assertThat(http.getForEntity("/api/v1/common-fixture/private",String.class).getStatusCode().value()).isEqualTo(401);
        var csrf=http.getForEntity("/api/v1/csrf",String.class);
        var request=HttpRequest.newBuilder(URI.create(http.getRootUri()+"/api/v1/common-fixture/public"))
            .header("Cookie",csrf.getHeaders().getFirst("Set-Cookie").split(";",2)[0])
            .header("X-XSRF-TOKEN",json.readTree(csrf.getBody()).path("data").path("token").asText())
            .POST(HttpRequest.BodyPublishers.noBody()).build();
        assertThat(HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(objects.projectPublic("FIXTURE_OBJECT","published",Map.of("name","公开名称","contact","private"))).containsOnlyKeys("name");
    }
    @Test void profileHttpReplaysWithFreshRequestIdsAndReportsVersionConflicts() throws Exception {
        User user=user(); String token=sessions.login(user.account(),"FixtureOnly123","common-http-"+user.id()).token();
        var csrf=http.getForEntity("/api/v1/csrf",String.class);
        String cookie="SAR_SESSION="+token+"; "+csrf.getHeaders().getFirst("Set-Cookie").split(";",2)[0];
        String csrfToken=json.readTree(csrf.getBody()).path("data").path("token").asText();
        var client=HttpClient.newHttpClient();
        var payload=new LinkedHashMap<String,Object>(Map.of("nickname","接口昵称","contact","private","expectedVersion",user.version(),"requestKey",key()));
        var first=patch(client,payload,cookie,csrfToken); var replay=patch(client,payload,cookie,csrfToken);
        assertThat(first.statusCode()).isEqualTo(200); assertThat(replay.statusCode()).isEqualTo(200);
        var a=json.readTree(first.body()); var b=json.readTree(replay.body());
        assertThat(a.path("data")).isEqualTo(b.path("data")); assertThat(a.path("requestId")).isNotEqualTo(b.path("requestId"));
        assertThat(first.body()).doesNotContain("passwordHash","FixtureOnly123");
        payload.put("nickname","冲突昵称"); assertThat(patch(client,payload,cookie,csrfToken).body()).contains("REQUEST_KEY_CONFLICT");
        payload.put("requestKey",key()); var stale=patch(client,payload,cookie,csrfToken);
        assertThat(stale.statusCode()).isEqualTo(409); assertThat(stale.body()).contains("VERSION_CONFLICT","currentVersion");
        payload.remove("requestKey"); assertThat(patch(client,payload,cookie,csrfToken).statusCode()).isEqualTo(400);
        assertThat(receipts(id(user))).isEqualTo(1);
    }
    HttpResponse<String> patch(HttpClient client,Map<String,Object> payload,String cookie,String csrf) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(http.getRootUri()+"/api/v1/accounts/me"))
            .header("Cookie",cookie).header("X-XSRF-TOKEN",csrf).header("Content-Type","application/json")
            .method("PATCH",HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build(),HttpResponse.BodyHandlers.ofString());
    }
    static class FixturePolicy implements ObjectPolicy {
        final Map<String,Long> owners=new ConcurrentHashMap<>();
        public String resourceType(){return "FIXTURE_OBJECT";}
        public Decision decide(CurrentAccess.Actor actor,String id,String action){
            if(action.equals("READ_PUBLIC") && id.equals("published"))return Decision.allow("name");
            if(!Objects.equals(owners.get(id),actor.id()))return Decision.deny();
            return switch(action){case "READ"->Decision.allow("id","name");case "WRITE"->Decision.allow();default->Decision.deny();};
        }
    }
    @TestConfiguration static class Fixtures {
        @Bean FixturePolicy fixturePolicy(){return new FixturePolicy();}
        @Bean FixtureController fixtureController(){return new FixtureController();}
    }
    @RestController static class FixtureController {
        @PublicRead @GetMapping("/api/v1/common-fixture/public") Object read(jakarta.servlet.http.HttpServletRequest request){return ApiResponse.success(Map.of("name","published"),request);}
        @GetMapping("/api/v1/common-fixture/private") Object privateRead(){throw new AssertionError("not authenticated");}
        @PublicRead @PostMapping("/api/v1/common-fixture/public") Object write(){throw new AssertionError("read annotation must not open writes");}
    }
}
