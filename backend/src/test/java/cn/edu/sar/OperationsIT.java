package cn.edu.sar;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.domain.access.*;
import cn.edu.sar.domain.operations.*;
import cn.edu.sar.persistence.access.AccessRepository;
import cn.edu.sar.jobs.OutboxJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="sar.outbox.enabled=false")
class OperationsIT {
    @Autowired AccessService access;
    @Autowired AccessRepository repo;
    @Autowired AuditWriter audits;
    @Autowired AuditService query;
    @Autowired OutboxService events;
    @Autowired EventAdministration adminEvents;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SessionService sessions;
    @Autowired ObjectMapper json;
    @Autowired TestRestTemplate http;
    @Autowired TestClock clock;
    TransactionTemplate tx; long admin,rescue,adoption,user;
    List<Long> created=new ArrayList<>();
    String suffix(){return UUID.randomUUID().toString().replace("-","");}
    @BeforeEach void setup(){
        clock.now.set(Instant.parse("2026-11-17T01:00:00Z"));tx=new TransactionTemplate(transactions);
        admin=account("SYSTEM_ADMIN");rescue=account("RESCUE_ADMIN");adoption=account("ADOPTION_ADMIN");user=account(null);
        repo.jdbc().execute("CREATE TABLE IF NOT EXISTS outbox_fixture_effect(event_id BIGINT PRIMARY KEY,value INT NOT NULL) ENGINE=InnoDB");
    }
    @AfterEach void cleanRoles(){for(long id:created)repo.jdbc().update("DELETE FROM role_grant WHERE user_id=?",id);}
    long account(String role){long id=Long.parseLong(access.register("op"+suffix().substring(0,16),"FixtureOnly123","运营测试").id());created.add(id);if(role!=null)repo.jdbc().update("INSERT INTO role_grant(user_id,role) VALUES(?,?)",id,role);return id;}
    long append(String module,String action,String request,String reason){return tx.execute(s->audits.append(new AuditWriter.Change(user,module,"FIXTURE",user,action,"PENDING","DONE",reason,request)));}
    String type(){return "FIXTURE_"+suffix().toUpperCase(Locale.ROOT);}
    OutboxService.Event event(String type){return tx.execute(s->events.append("fixture:"+suffix(),type,"FIXTURE",user,Map.of("actorId",Long.toString(user))));}
    OutboxHandler handler(String type,boolean fail){return new OutboxHandler(){public String eventType(){return type;}public void handle(OutboxService.Event e){repo.jdbc().update("INSERT INTO outbox_fixture_effect(event_id,value) VALUES(?,1)",Long.parseLong(e.id()));if(fail)throw new IllegalStateException("secret-password@example.invalid");}};}
    long effects(String id){return repo.jdbc().queryForObject("SELECT COUNT(*) FROM outbox_fixture_effect WHERE event_id=?",Long.class,Long.parseLong(id));}
    @Test void auditAndEventRequireOuterTransactionAndRollbackWithBusiness(){
        assertThatThrownBy(()->audits.append(new AuditWriter.Change(user,"ACCESS","ACCOUNT",user,"FIXTURE","","","测试","mandatory"))).isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(()->events.append("fixture:mandatory","FIXTURE","ACCOUNT",user,Map.of())).isInstanceOf(IllegalTransactionStateException.class);
        long version=access.me(user).version();String key="fixture:"+suffix(),request=suffix();
        assertThatThrownBy(()->tx.execute(s->{
            access.profile(user,"事务测试","",version);audits.append(new AuditWriter.Change(user,"ACCESS","ACCOUNT",user,"FIXTURE","","DONE","失败整体回滚",request));
            events.append(key,"FIXTURE","ACCOUNT",user,Map.of("actorId",Long.toString(user)));throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(access.me(user).version()).isEqualTo(version);
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_log WHERE request_id=?",Long.class,request)).isZero();
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_key=?",Long.class,key)).isZero();
    }
    @Test void eventOrAuditFailureRollsBackEverythingAndAccountChangesEmitReferences(){
        long version=access.me(user).version();String request=suffix();
        assertThatThrownBy(()->tx.execute(s->{access.profile(user,"失败测试","",version);audits.append(new AuditWriter.Change(user,"ACCESS","ACCOUNT",user,"FIXTURE","","DONE","整体回滚",request));events.append("fixture:"+suffix(),"FIXTURE","ACCOUNT",user,Map.of("material","不允许完整申请材料"));return null;})).isInstanceOf(IllegalArgumentException.class);
        assertThat(access.me(user).version()).isEqualTo(version);
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_log WHERE request_id=?",Long.class,request)).isZero();
        String eventKey="fixture:"+suffix();
        assertThatThrownBy(()->tx.execute(s->{events.append(eventKey,"FIXTURE","ACCOUNT",user,Map.of());audits.append(new AuditWriter.Change(user,"ACCESS","ACCOUNT",user,"FIXTURE","","DONE","x".repeat(501),request));return null;})).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_key=?",Long.class,eventKey)).isZero();
        access.roles(admin,user,List.of("RESCUE_ADMIN"),version,"测试角色变化与事件一致提交",request);
        long audit=repo.jdbc().queryForObject("SELECT id FROM audit_log WHERE request_id=?",Long.class,request);
        String payload=repo.jdbc().queryForObject("SELECT payload FROM outbox_event WHERE event_key=?",String.class,"AUDIT:"+audit+":ACCESS");
        assertThat(payload).contains("auditId","actorId","targetUserId").doesNotContain("password","reason","material");
    }
    @Test void dutyScopesFiltersRedactionAndRevocationApplyToEveryRead(){
        String request=suffix();long a=append("RESCUE","TASK_ACCEPTED",request,"联系电话13812345678，邮箱someone@example.invalid，password=Secret123");
        long b=append("ADOPTION","APPLICATION_REVIEWED",request,"领养处理摘要");
        long qualification=tx.execute(s->audits.append(new AuditWriter.Change(user,"ACCESS","ACCOUNT",user,"VOLUNTEER_APPLIED","NONE","PENDING","资格摘要",request)));
        var rows=query.search(rescue,"",null,request,"",null,null,1,20);
        assertThat(rows.items()).extracting(AuditService.Audit::id).containsExactlyInAnyOrder(Long.toString(a),Long.toString(qualification));
        assertThat(rows.items().toString()).doesNotContain("13812345678","someone@example.invalid","Secret123");
        assertThat(query.search(adoption,"",null,request,"",null,null,1,20).items()).extracting(AuditService.Audit::id).containsExactly(Long.toString(b));
        assertThat(query.search(admin,"FIXTURE",user,request,"ADOPTION",clock.instant().minusSeconds(1),clock.instant().plusSeconds(1),1,20).total()).isEqualTo(1);
        assertThatThrownBy(()->query.detail(rescue,b)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status().value()).isEqualTo(404));
        assertThatThrownBy(()->query.search(user,"",null,"","",null,null,1,20)).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.status().value()).isEqualTo(403));
        repo.jdbc().update("DELETE FROM role_grant WHERE user_id=?",rescue);
        assertThatThrownBy(()->query.detail(rescue,a)).isInstanceOf(BusinessException.class);
    }
    @Test void correctionAppendsRelatedRecordAndPreservesOriginal(){
        long id=append("RESCUE","TASK_ACCEPTED",suffix(),"原始处理摘要");var before=query.detail(admin,id).record();
        var note=query.correct(rescue,id,"经复核补充说明，具体业务更正另行办理",suffix());
        var detail=query.detail(rescue,Long.parseLong(note.id()));
        assertThat(detail.record()).isEqualTo(before);assertThat(detail.corrections()).hasSize(1);assertThat(note.correctsId()).isEqualTo(Long.toString(id));
        assertThatThrownBy(()->query.correct(adoption,id,"不属于本人职责范围的操作说明",suffix())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->query.correct(admin,Long.parseLong(note.id()),"不能对纠错记录直接再追加记录",suffix())).isInstanceOf(BusinessException.class);
    }
    @Test void eventKeyIsStableAndUnhandledTypesArePreserved(){
        String type=type(),key="fixture:"+suffix();
        var one=tx.execute(s->events.append(key,type,"FIXTURE",user,Map.of("actorId",Long.toString(user),"status","DONE")));
        var two=tx.execute(s->events.append(key,type,"FIXTURE",user,new LinkedHashMap<>(Map.of("status","DONE","actorId",Long.toString(user)))));
        assertThat(two.id()).isEqualTo(one.id());
        assertThatThrownBy(()->tx.execute(s->events.append(key,type,"FIXTURE",user,Map.of("status","PENDING")))).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.code()).isEqualTo("EVENT_KEY_CONFLICT"));
        new OutboxJob(events,List.of()).run();assertThat(events.get(Long.parseLong(one.id()),false).status()).isEqualTo("PENDING");
    }
    @Test void concurrentWorkersClaimEachEventOnlyOnce()throws Exception{
        String type=type();var one=event(type);var two=event(type);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(8);
        try{List<Future<Optional<OutboxService.Event>>> futures=new ArrayList<>();for(int i=0;i<8;i++)futures.add(pool.submit(()->{start.await();return events.claim(Set.of(type));}));start.countDown();
            Set<String> ids=new HashSet<>();int claimed=0;for(var f:futures){var result=f.get(20,TimeUnit.SECONDS);if(result.isPresent()){claimed++;ids.add(result.get().id());}}
            // SKIP LOCKED may return empty while another scan temporarily locks both candidates.
            // A subsequent poll must recover every skipped event, without repeating an existing lease.
            assertThat(claimed).isBetween(1,2);assertThat(ids).hasSize(claimed);
            for(int i=0;i<2;i++){var remaining=events.claim(Set.of(type));if(remaining.isEmpty())break;claimed++;assertThat(ids.add(remaining.get().id())).isTrue();}
            assertThat(claimed).isEqualTo(2);assertThat(ids).containsExactlyInAnyOrder(one.id(),two.id());
        }finally{pool.shutdownNow();}
    }
    @Test void concurrentProducersWithExistingSnapshotsReuseTheSameFact()throws Exception{
        String type=type(),key="fixture:"+suffix();var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{List<Future<String>> ids=new ArrayList<>();for(int i=0;i<2;i++)ids.add(pool.submit(()->tx.execute(s->{
            repo.jdbc().queryForObject("SELECT COUNT(*) FROM account",Long.class);ready.countDown();
            try{if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            return events.append(key,type,"FIXTURE",user,Map.of("actorId",Long.toString(user))).id();
        })));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();assertThat(ids.get(0).get(20,TimeUnit.SECONDS)).isEqualTo(ids.get(1).get(20,TimeUnit.SECONDS));
        }finally{start.countDown();pool.shutdownNow();}
    }
    @Test void failedConsumerRollsBackSideEffectsThenRetriesWithoutLeakingError(){
        String type=type();var event=event(type);var first=events.claim(Set.of(type)).orElseThrow();
        assertThat(events.deliver(first,handler(type,true))).isFalse();assertThat(effects(event.id())).isZero();
        var retry=events.get(Long.parseLong(event.id()),false);assertThat(retry.status()).isEqualTo("RETRY");assertThat(retry.lastError()).isEqualTo("CONSUMER_FAILED");assertThat(events.claim(Set.of(type))).isEmpty();
        clock.now.set(retry.availableAt());var second=events.claim(Set.of(type)).orElseThrow();
        assertThat(events.deliver(second,handler(type,false))).isTrue();assertThat(effects(event.id())).isEqualTo(1);
        assertThat(events.deliver(second,handler(type,false))).isFalse();assertThat(events.claim(Set.of(type))).isEmpty();
    }
    @Test void expiredLeaseCanRecoverAndStaleWorkerCannotAcknowledge(){
        String type=type();var event=event(type);var old=events.claim(Set.of(type)).orElseThrow();clock.now.set(clock.instant().plusSeconds(61));
        var recovered=events.claim(Set.of(type)).orElseThrow();assertThat(recovered.leaseToken()).isNotEqualTo(old.leaseToken());
        assertThat(events.deliver(old,handler(type,false))).isFalse();assertThat(effects(event.id())).isZero();
        assertThat(events.deliver(recovered,handler(type,false))).isTrue();assertThat(effects(event.id())).isEqualTo(1);
    }
    @Test void eightFailuresNeedManualRetryAndSuccessfulEventsCannotReset(){
        String type=type();var event=event(type);
        for(int i=0;i<8;i++){var claim=events.claim(Set.of(type)).orElseThrow();events.deliver(claim,handler(type,true));clock.now.set(events.get(Long.parseLong(event.id()),false).availableAt());}
        assertThat(events.get(Long.parseLong(event.id()),false).status()).isEqualTo("DEAD");assertThat(events.claim(Set.of(type))).isEmpty();
        assertThatThrownBy(()->adminEvents.retry(rescue,Long.parseLong(event.id()),"管理员处理故障后登记重试",suffix())).isInstanceOf(BusinessException.class);
        adminEvents.retry(admin,Long.parseLong(event.id()),"管理员处理故障后登记重试",suffix());
        var fresh=events.claim(Set.of(type)).orElseThrow();assertThat(fresh.attempts()).isEqualTo(1);events.deliver(fresh,handler(type,false));
        assertThatThrownBy(()->adminEvents.retry(admin,Long.parseLong(event.id()),"已成功的事件不能再次重试",suffix())).isInstanceOf(BusinessException.class);
    }
    @Test void scheduledJobUsesRegisteredHandlerAndBadRegistrationsFail(){
        String type=type();var event=event(type);new OutboxJob(events,List.of(handler(type,false))).run();
        assertThat(events.get(Long.parseLong(event.id()),false).status()).isEqualTo("DONE");
        assertThatThrownBy(()->new OutboxJob(events,List.of(handler(type,false),handler(type,false)))).isInstanceOf(IllegalStateException.class);
        var monitor=adminEvents.search(admin,"DONE",1,20);assertThat(monitor.counts().get("DONE")).isPositive();
        assertThatThrownBy(()->adminEvents.search(rescue,"",1,20)).isInstanceOf(BusinessException.class);
    }
    @Test void httpCorrectionsAreIdempotentAndOriginalCannotBeOverwritten()throws Exception{
        long id=append("ACCESS","FIXTURE",suffix(),"原始审计摘要");
        String token=sessions.login(access.me(admin).account(),"FixtureOnly123","operations-http-"+suffix()).token();
        var csrf=http.getForEntity("/api/v1/csrf",String.class);
        String cookie="SAR_SESSION="+token+"; "+csrf.getHeaders().getFirst("Set-Cookie").split(";",2)[0];String csrfToken=json.readTree(csrf.getBody()).path("data").path("token").asText();
        String body=json.writeValueAsString(Map.of("reason","经核实补充关联纠错说明，保持原始记录","requestKey",suffix()));
        String path="/api/v1/audit-logs/"+id+"/corrections";var one=call(path,"POST",body,cookie,csrfToken);var two=call(path,"POST",body,cookie,csrfToken);
        assertThat(one.statusCode()).isEqualTo(200);assertThat(json.readTree(one.body()).path("data")).isEqualTo(json.readTree(two.body()).path("data"));assertThat(query.detail(admin,id).corrections()).hasSize(1);
        assertThat(call("/api/v1/audit-logs/"+id,"PATCH","{}",cookie,csrfToken).statusCode()).isEqualTo(405);
        assertThat(http.getForEntity("/api/v1/audit-logs",String.class).getStatusCode().value()).isEqualTo(401);
        String normal=sessions.login(access.me(user).account(),"FixtureOnly123","ordinary-"+suffix()).token();
        assertThat(call("/api/v1/outbox-events","GET","","SAR_SESSION="+normal,"").statusCode()).isEqualTo(403);
        String monitor=call("/api/v1/outbox-events","GET","",cookie,csrfToken).body();assertThat(monitor).doesNotContain("leaseToken","payload","payloadHash");
        var failed=event(type());long eventId=Long.parseLong(failed.id());
        repo.jdbc().update("UPDATE outbox_event SET status='DEAD',attempts=8 WHERE id=?",eventId);
        String retryPath="/api/v1/outbox-events/"+eventId+"/retry",retryBody=json.writeValueAsString(Map.of("reason","已检查故障原因并申请重新处理事件","requestKey",suffix()));
        var retried=call(retryPath,"POST",retryBody,cookie,csrfToken);var replay=call(retryPath,"POST",retryBody,cookie,csrfToken);
        assertThat(retried.statusCode()).isEqualTo(200);assertThat(json.readTree(retried.body()).path("data")).isEqualTo(json.readTree(replay.body()).path("data"));
        assertThat(repo.jdbc().queryForObject("SELECT COUNT(*) FROM audit_log WHERE object_type='OUTBOX_EVENT' AND object_id=?",Long.class,eventId)).isEqualTo(1);
        repo.jdbc().update("DELETE FROM role_grant WHERE user_id=?",admin);
        assertThat(call(retryPath,"POST",retryBody,cookie,csrfToken).statusCode()).isEqualTo(403);
    }
    HttpResponse<String> call(String path,String method,String body,String cookie,String csrf)throws Exception{
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(http.getRootUri()+path)).header("Cookie",cookie).header("X-XSRF-TOKEN",csrf).header("Content-Type","application/json").method(method,body.isEmpty()?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    static class TestClock extends Clock {AtomicReference<Instant> now=new AtomicReference<>(Instant.EPOCH);public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}}
    @TestConfiguration static class Fixtures {@Bean @Primary TestClock operationClock(){return new TestClock();}}
}
