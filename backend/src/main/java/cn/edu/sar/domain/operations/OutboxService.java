package cn.edu.sar.domain.operations;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.domain.access.SessionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import static cn.edu.sar.persistence.access.AccessRepository.at;

@Service
public class OutboxService {
    public record Event(String id,String eventKey,String eventType,String objectType,String objectId,Map<String,String> payload,
                        String status,int attempts,Instant availableAt,String leaseToken,Instant leaseUntil,String lastError,Instant createdAt,Instant completedAt) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final TransactionTemplate isolated;
    public OutboxService(JdbcTemplate jdbc,ObjectMapper json,Clock clock,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.json=json;this.clock=clock;isolated=new TransactionTemplate(manager);isolated.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);isolated.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Event append(String key,String type,String objectType,long objectId,Map<String,String> references){
        if(key==null || !key.matches("[A-Za-z0-9_.:-]{8,96}") || !AuditWriter.token(type,64) || !AuditWriter.token(objectType,32) || objectId<=0 || references==null || references.size()>20)
            throw new IllegalArgumentException("Invalid event metadata");
        var payload=new TreeMap<String,String>(references);
        payload.forEach((name,value)->{
            boolean id=name.matches("[a-zA-Z][a-zA-Z0-9]{0,35}Id") && value!=null && value.matches("[1-9][0-9]{0,18}");
            boolean state=Set.of("status","decision","reasonCode").contains(name) && value!=null && value.matches("[A-Z][A-Z0-9_]{0,63}");
            boolean version=name.equals("version") && value!=null && value.matches("[0-9]{1,19}");
            if(!id && !state && !version)throw new IllegalArgumentException("Event payload accepts references and state codes only");
        });
        String encoded=encode(payload), hash=SessionService.digest(type+"\n"+objectType+"\n"+objectId+"\n"+encoded);
        jdbc.update("INSERT INTO outbox_event(event_key,event_type,object_type,object_id,payload,payload_hash,available_at,created_at) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE event_key=event_key",
            key,type,objectType,objectId,encoded,hash,at(clock.instant()),at(clock.instant()));
        var event=jdbc.query("SELECT * FROM outbox_event WHERE event_key=? FOR UPDATE",mapper,key).get(0);
        String saved=jdbc.queryForObject("SELECT payload_hash FROM outbox_event WHERE id=? FOR UPDATE",String.class,Long.parseLong(event.id()));
        if(!hash.equals(saved))throw new BusinessException(HttpStatus.CONFLICT,"EVENT_KEY_CONFLICT","同一事件键不能登记不同事实");
        return event;
    }
    /** Claim only registered types. Unhandled events stay pending until their consumer is installed. */
    public Optional<Event> claim(Set<String> types){
        if(types.isEmpty())return Optional.empty();
        var sorted=new TreeSet<>(types);if(sorted.stream().anyMatch(t->!AuditWriter.token(t,64)))throw new IllegalArgumentException("Invalid consumer type");
        return isolated.execute(tx->{
            var args=new ArrayList<Object>();args.addAll(sorted);args.add(at(clock.instant()));args.add(at(clock.instant()));
            String placeholders=String.join(",",Collections.nCopies(sorted.size(),"?"));
            var rows=jdbc.query("SELECT * FROM outbox_event WHERE event_type IN ("+placeholders+") AND (((status='PENDING' OR status='RETRY') AND available_at<=?) OR (status='PROCESSING' AND lease_until<=?)) ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",mapper,args.toArray());
            if(rows.isEmpty())return Optional.empty();
            long id=Long.parseLong(rows.get(0).id());String token=UUID.randomUUID().toString();
            jdbc.update("UPDATE outbox_event SET status='PROCESSING',attempts=attempts+1,lease_token=?,lease_until=? WHERE id=?",token,at(clock.instant().plusSeconds(60)),id);
            return Optional.of(get(id,true));
        });
    }
    /** Consumer database writes and DONE acknowledgement share one transaction. */
    public boolean deliver(Event claim,OutboxHandler handler){
        try {
            return Boolean.TRUE.equals(isolated.execute(tx->{
                Event live=get(Long.parseLong(claim.id()),true);
                if(!owned(live,claim) || !live.leaseUntil().isAfter(clock.instant()))return false;
                if(!handler.eventType().equals(live.eventType()))throw new IllegalArgumentException("Handler type mismatch");
                handler.handle(live);
                if(!live.leaseUntil().isAfter(clock.instant()))throw new IllegalStateException("Consumer exceeded lease");
                jdbc.update("UPDATE outbox_event SET status='DONE',completed_at=?,lease_token=NULL,lease_until=NULL,last_error='' WHERE id=?",at(clock.instant()),Long.parseLong(live.id()));
                return true;
            }));
        }catch(RuntimeException failure){
            isolated.executeWithoutResult(tx->{
                Event live=get(Long.parseLong(claim.id()),true);
                if(!owned(live,claim))return;
                boolean dead=live.attempts()>=8;
                long delay=Math.min(3600,5L << Math.min(live.attempts()-1,10));
                jdbc.update("UPDATE outbox_event SET status=?,available_at=?,lease_token=NULL,lease_until=NULL,last_error=? WHERE id=?",
                    dead?"DEAD":"RETRY",at(clock.instant().plusSeconds(delay)),"CONSUMER_FAILED",Long.parseLong(live.id()));
            });
            return false;
        }
    }
    private boolean owned(Event live,Event claim){return live!=null && live.status().equals("PROCESSING") && Objects.equals(live.leaseToken(),claim.leaseToken());}
    public Event get(long id,boolean lock){return jdbc.query("SELECT * FROM outbox_event WHERE id=?"+(lock?" FOR UPDATE":""),mapper,id).stream().findFirst().orElse(null);}
    private String encode(Map<String,String> value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException(e);}}
    private Map<String,String> decode(String value){
        try{return json.readValue(value,json.getTypeFactory().constructMapType(Map.class,String.class,String.class));}
        catch(JsonProcessingException e){throw new IllegalStateException("Invalid stored event",e);}
    }
    private Instant time(java.sql.ResultSet r,String name)throws java.sql.SQLException {var t=r.getObject(name,LocalDateTime.class);return t==null?null:t.toInstant(ZoneOffset.UTC);}
    private final RowMapper<Event> mapper=(r,n)->{
        Map<String,String> payload=decode(r.getString("payload"));
        return new Event(r.getString("id"),r.getString("event_key"),r.getString("event_type"),r.getString("object_type"),r.getString("object_id"),Map.copyOf(payload),r.getString("status"),r.getInt("attempts"),time(r,"available_at"),r.getString("lease_token"),time(r,"lease_until"),r.getString("last_error"),time(r,"created_at"),time(r,"completed_at"));
    };
}
