package cn.edu.sar.web.operations;

import cn.edu.sar.common.api.ApiResponse;
import cn.edu.sar.common.command.IdempotencyService;
import cn.edu.sar.common.web.*;
import cn.edu.sar.domain.operations.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class OperationsController {
    public record Reason(@NotBlank @Size(min=10,max=500) String reason,@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{8,64}") String requestKey) {}
    private final AuditService audits;
    private final EventAdministration events;
    private final IdempotencyService commands;
    public OperationsController(AuditService audits,EventAdministration events,IdempotencyService commands){this.audits=audits;this.events=events;this.commands=commands;}
    private long actor(HttpServletRequest request){return AccessInterceptor.current(request).id();}
    @GetMapping("/audit-logs")
    Object search(@RequestParam(defaultValue="") @Size(max=32) String objectType,@RequestParam(required=false) @Positive Long objectId,
        @RequestParam(defaultValue="") @Size(max=64) String requestId,@RequestParam(defaultValue="") @Size(max=16) String module,
        @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int pageSize,HttpServletRequest request){
        return ApiResponse.success(audits.search(actor(request),objectType,objectId,requestId,module,from,to,page,pageSize),request);
    }
    @GetMapping("/audit-logs/{id}") Object detail(@PathVariable @Positive long id,HttpServletRequest request){return ApiResponse.success(audits.detail(actor(request),id),request);}
    @PostMapping("/audit-logs/{id}/corrections") Object correct(@PathVariable @Positive long id,@Valid @RequestBody Reason input,HttpServletRequest request){
        long actor=actor(request);var data=commands.execute(actor,"AUDIT.CORRECT.V1",input.requestKey(),Map.of("id",id,"reason",input.reason()),AuditService.Audit.class,
            ()->audits.requireView(actor,id,false),()->audits.correct(actor,id,input.reason(),RequestIdFilter.get(request)));
        return ApiResponse.success(data,request);
    }
    @GetMapping("/outbox-events") Object events(@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(50) int pageSize,HttpServletRequest request){return ApiResponse.success(events.search(actor(request),status,page,pageSize),request);}
    @PostMapping("/outbox-events/{id}/retry") Object retry(@PathVariable @Positive long id,@Valid @RequestBody Reason input,HttpServletRequest request){
        long actor=actor(request);var data=commands.execute(actor,"EVENT.RETRY.V1",input.requestKey(),Map.of("id",id,"reason",input.reason()),EventAdministration.Summary.class,
            ()->events.requireAdmin(actor),()->events.retry(actor,id,input.reason(),RequestIdFilter.get(request)));
        return ApiResponse.success(data,request);
    }
}
