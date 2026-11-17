package cn.edu.sar.domain.operations;

import java.sql.Statement;
import java.time.Clock;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static cn.edu.sar.persistence.access.AccessRepository.at;

@Service
public class AuditWriter {
    public static final Set<String> MODULES=Set.of("ACCESS","RESCUE","ANIMALCARE","ADOPTION","FOLLOWUP","OPERATIONS");
    public record Change(long actorId,String module,String objectType,long objectId,String action,
                         String beforeState,String afterState,String reason,String requestId) {}
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public AuditWriter(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    @Transactional(propagation=Propagation.MANDATORY)
    public long append(Change change){return insert(change,null);}
    @Transactional(propagation=Propagation.MANDATORY)
    public long correction(Change change,long originalId){return insert(change,originalId);}
    private long insert(Change c,Long corrects){
        if(c.actorId()<0 || !MODULES.contains(c.module()) || c.objectId()<=0 || !token(c.objectType(),32) || !token(c.action(),48)
            || c.requestId()==null || c.requestId().isBlank() || c.requestId().length()>64)
            throw new IllegalArgumentException("Invalid audit metadata");
        for(String snapshot:new String[]{c.beforeState(),c.afterState()})
            if(snapshot==null || !snapshot.matches("[A-Z0-9_.,:;=\\[\\]{}() /-]{0,500}"))
                throw new IllegalArgumentException("Audit snapshots must be state codes or reference IDs, never raw personal data");
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{
            var s=connection.prepareStatement("INSERT INTO audit_log(actor_id,module,object_type,object_id,action,before_state,after_state,reason,request_id,created_at,corrects_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            s.setLong(1,c.actorId());s.setString(2,c.module());s.setString(3,c.objectType());s.setLong(4,c.objectId());s.setString(5,c.action());
            s.setString(6,c.beforeState());s.setString(7,c.afterState());s.setString(8,redact(c.reason()));s.setString(9,c.requestId());
            s.setObject(10,at(clock.instant()));s.setObject(11,corrects);return s;
        },key);
        return key.getKey().longValue();
    }
    public static boolean token(String value,int max){return value!=null && value.length()<=max && value.matches("[A-Z][A-Z0-9_]*");}
    public static String redact(String value){
        if(value==null)return "";
        return value.replaceAll("(?i)(password|token|密码|令牌)\\s*[:=：]\\s*[^\\s，,；;]+","[凭据已隐藏]")
            .replaceAll("(?<![A-Za-z0-9])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[邮箱已隐藏]")
            .replaceAll("(?<![0-9])[0-9]{7,18}[0-9Xx]?(?![0-9])","[号码已隐藏]")
            .replaceAll("-?[0-9]{1,3}\\.[0-9]{5,}","[位置已隐藏]");
    }
}
