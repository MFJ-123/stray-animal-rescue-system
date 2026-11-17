package cn.edu.sar.jobs;

import cn.edu.sar.domain.operations.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="sar.outbox.enabled",havingValue="true",matchIfMissing=true)
public class OutboxJob {
    private static final Logger log=LoggerFactory.getLogger(OutboxJob.class);
    private final OutboxService outbox;
    private final Map<String,OutboxHandler> handlers=new TreeMap<>();
    public OutboxJob(OutboxService outbox,List<OutboxHandler> consumers){
        this.outbox=outbox;
        for(var consumer:consumers)if(!AuditWriter.token(consumer.eventType(),64) || handlers.putIfAbsent(consumer.eventType(),consumer)!=null)
            throw new IllegalStateException("Duplicate or invalid outbox handler");
    }
    @Scheduled(fixedDelayString="${sar.outbox.delay-ms:5000}",initialDelayString="${sar.outbox.delay-ms:5000}")
    public void run(){
        try{for(int i=0;i<20;i++){
            var claim=outbox.claim(handlers.keySet());if(claim.isEmpty())break;
            outbox.deliver(claim.get(),handlers.get(claim.get().eventType()));
        }}catch(RuntimeException failure){log.warn("Outbox scan failed: {}",failure.getClass().getSimpleName());}
    }
    @Configuration @EnableScheduling static class Scheduling {}
}
