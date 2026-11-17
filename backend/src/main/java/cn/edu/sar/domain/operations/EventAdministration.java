package cn.edu.sar.domain.operations;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.common.security.CurrentAccess;
import cn.edu.sar.domain.access.AccessModels.Page;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.edu.sar.persistence.access.AccessRepository.at;

@Service
public class EventAdministration {
    public record Summary(String id,String eventType,String objectType,String objectId,String status,int attempts,Instant availableAt,Instant leaseUntil,String lastError,Instant createdAt) {}
    public record Monitor(Page<Summary> events,Map<String,Long> counts,Instant oldestPendingAt,Set<String> registeredConsumers) {}
    private final CurrentAccess access;
    private final JdbcTemplate jdbc;
    private final OutboxService outbox;
    private final AuditWriter audits;
    private final Clock clock;
    private final Set<String> consumers;
    public EventAdministration(CurrentAccess access,JdbcTemplate jdbc,OutboxService outbox,AuditWriter audits,Clock clock,List<OutboxHandler> handlers){
        this.access=access;this.jdbc=jdbc;this.outbox=outbox;this.audits=audits;this.clock=clock;
        var types=new TreeSet<String>();for(var h:handlers)if(!types.add(h.eventType()))throw new IllegalStateException("Duplicate event handler");consumers=Set.copyOf(types);
    }
    public void requireAdmin(long actor){access.requireRole(actor,"SYSTEM_ADMIN");}
    public Monitor search(long actor,String status,int page,int size){
        requireAdmin(actor);
        if(!status.isEmpty() && !Set.of("PENDING","PROCESSING","RETRY","DEAD","DONE").contains(status))throw new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_ARGUMENT","事件状态无效");
        String where=status.isEmpty()?"":" WHERE status=?";
        var args=new ArrayList<Object>();if(!status.isEmpty())args.add(status);
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event"+where,Long.class,args.toArray());
        args.add(size);args.add((page-1)*size);
        var rows=jdbc.query("SELECT * FROM outbox_event"+where+" ORDER BY id DESC LIMIT ? OFFSET ?",(r,n)->new Summary(r.getString("id"),r.getString("event_type"),r.getString("object_type"),r.getString("object_id"),r.getString("status"),r.getInt("attempts"),time(r,"available_at"),time(r,"lease_until"),r.getString("last_error"),time(r,"created_at")),args.toArray());
        var counts=new TreeMap<String,Long>();for(String state:List.of("PENDING","PROCESSING","RETRY","DEAD","DONE"))counts.put(state,0L);
        jdbc.query("SELECT status,COUNT(*) AS n FROM outbox_event GROUP BY status",r->{counts.put(r.getString("status"),r.getLong("n"));});
        LocalDateTime oldest=jdbc.queryForObject("SELECT MIN(created_at) FROM outbox_event WHERE status<>'DONE'",LocalDateTime.class);
        return new Monitor(new Page<>(rows,total,page,size),counts,oldest==null?null:oldest.toInstant(ZoneOffset.UTC),consumers);
    }
    @Transactional
    public Summary retry(long actor,long id,String reason,String requestId){
        requireAdmin(actor);var event=outbox.get(id,true);
        if(event==null)throw new BusinessException(HttpStatus.NOT_FOUND,"EVENT_NOT_FOUND","事件不存在");
        if(!Set.of("DEAD","RETRY").contains(event.status()))throw new BusinessException(HttpStatus.CONFLICT,"EVENT_NOT_RETRYABLE","当前事件无需人工重试，请刷新状态");
        jdbc.update("UPDATE outbox_event SET status='PENDING',attempts=0,available_at=?,lease_token=NULL,lease_until=NULL,last_error='' WHERE id=?",at(clock.instant()),id);
        audits.append(new AuditWriter.Change(actor,"OPERATIONS","OUTBOX_EVENT",id,"EVENT_RETRY_REQUESTED",event.status(),"PENDING",reason,requestId));
        return new Summary(event.id(),event.eventType(),event.objectType(),event.objectId(),"PENDING",0,clock.instant(),null,"",event.createdAt());
    }
    private Instant time(java.sql.ResultSet r,String name)throws java.sql.SQLException {var t=r.getObject(name,LocalDateTime.class);return t==null?null:t.toInstant(ZoneOffset.UTC);}
}
