package cn.edu.sar.domain.operations;

import cn.edu.sar.common.api.BusinessException;
import cn.edu.sar.common.security.CurrentAccess;
import cn.edu.sar.domain.access.AccessModels.Page;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static cn.edu.sar.persistence.access.AccessRepository.at;

@Service
public class AuditService {
    public record Audit(String id,String actorId,String module,String objectType,String objectId,String action,
                        String beforeState,String afterState,String reason,String requestId,Instant createdAt,String correctsId) {}
    public record Detail(Audit record,List<Audit> corrections) {}
    private final JdbcTemplate jdbc;
    private final CurrentAccess access;
    private final AuditWriter writer;
    private final OutboxService events;
    public AuditService(JdbcTemplate jdbc,CurrentAccess access,AuditWriter writer,OutboxService events){this.jdbc=jdbc;this.access=access;this.writer=writer;this.events=events;}
    private String scope(long actor){
        var roles=access.load(actor).roles();
        if(roles.contains("SYSTEM_ADMIN"))return "1=1";
        List<String> parts=new ArrayList<>();
        if(roles.contains("RESCUE_ADMIN"))parts.add("(a.module IN ('RESCUE','ANIMALCARE') OR (a.module='ACCESS' AND COALESCE(p.action,a.action) IN ('VOLUNTEER_APPLIED','VOLUNTEER_REVIEWED','VOLUNTEER_SUSPENDED')))");
        if(roles.contains("ADOPTION_ADMIN"))parts.add("a.module IN ('ADOPTION','FOLLOWUP')");
        if(parts.isEmpty())throw new BusinessException(HttpStatus.FORBIDDEN,"AUDIT_FORBIDDEN","当前角色没有审计查询权限");
        return "("+String.join(" OR ",parts)+")";
    }
    private static final String FROM=" FROM audit_log a LEFT JOIN audit_log p ON p.id=a.corrects_id ";
    public Page<Audit> search(long actor,String objectType,Long objectId,String requestId,String module,Instant from,Instant to,int page,int size){
        var args=new ArrayList<Object>();String where=" WHERE "+scope(actor);
        if(!module.isEmpty()){if(!AuditWriter.MODULES.contains(module))throw invalid();where+=" AND a.module=?";args.add(module);}
        if(!objectType.isEmpty()){if(!AuditWriter.token(objectType,32))throw invalid();where+=" AND a.object_type=?";args.add(objectType);}
        if(objectId!=null){if(objectId<=0)throw invalid();where+=" AND a.object_id=?";args.add(objectId);}
        if(!requestId.isEmpty()){if(requestId.length()>64)throw invalid();where+=" AND a.request_id=?";args.add(requestId);}
        if(from!=null){where+=" AND a.created_at>=?";args.add(at(from));}
        if(to!=null){where+=" AND a.created_at<?";args.add(at(to));}
        if(from!=null && to!=null && !from.isBefore(to))throw invalid();
        long count=jdbc.queryForObject("SELECT COUNT(*)"+FROM+where,Long.class,args.toArray());
        args.add(size);args.add((page-1)*size);
        var rows=jdbc.query("SELECT a.*"+FROM+where+" ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",mapper,args.toArray());
        return new Page<>(rows,count,page,size);
    }
    public Audit requireView(long actor,long id,boolean lock){
        String permission=scope(actor);
        var rows=jdbc.query("SELECT a.*"+FROM+" WHERE a.id=? AND ("+permission+")"+(lock?" FOR UPDATE":""),mapper,id);
        if(rows.isEmpty())throw new BusinessException(HttpStatus.NOT_FOUND,"AUDIT_NOT_FOUND","记录不存在或不在当前职责范围内");
        return rows.get(0);
    }
    public Detail detail(long actor,long id){
        Audit selected=requireView(actor,id,false);
        long original=selected.correctsId()==null?id:Long.parseLong(selected.correctsId());
        return new Detail(requireView(actor,original,false),jdbc.query("SELECT a.*"+FROM+" WHERE a.corrects_id=? AND ("+scope(actor)+") ORDER BY a.id",mapper,original));
    }
    @Transactional
    public Audit correct(long actor,long originalId,String reason,String requestId){
        Audit original=requireView(actor,originalId,true);
        if(original.correctsId()!=null)throw new BusinessException(HttpStatus.CONFLICT,"ORIGINAL_AUDIT_REQUIRED","请在原始记录下追加纠错说明");
        long id=writer.correction(new AuditWriter.Change(actor,original.module(),original.objectType(),Long.parseLong(original.objectId()),"AUDIT_CORRECTION","","",reason,requestId),originalId);
        events.append("AUDIT:"+id+":CORRECTION","AUDIT_CORRECTED",original.objectType(),Long.parseLong(original.objectId()),
            Map.of("actorId",Long.toString(actor),"auditId",Long.toString(id),"correctsAuditId",Long.toString(originalId)));
        return requireView(actor,id,false);
    }
    private BusinessException invalid(){return new BusinessException(HttpStatus.BAD_REQUEST,"INVALID_ARGUMENT","请检查审计筛选条件和时间范围");}
    private final RowMapper<Audit> mapper=(r,n)->new Audit(r.getString("id"),r.getString("actor_id"),r.getString("module"),r.getString("object_type"),r.getString("object_id"),r.getString("action"),
        r.getString("before_state"),r.getString("after_state"),AuditWriter.redact(r.getString("reason")),r.getString("request_id"),r.getObject("created_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getString("corrects_id"));
}
