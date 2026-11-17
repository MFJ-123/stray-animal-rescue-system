package cn.edu.sar.web.access;

import cn.edu.sar.common.api.ApiResponse;
import cn.edu.sar.common.command.IdempotencyService;
import cn.edu.sar.common.security.ObjectAuthorization;
import cn.edu.sar.domain.access.AccessModels.User;
import cn.edu.sar.common.web.AccessInterceptor;
import cn.edu.sar.common.web.RequestIdFilter;
import cn.edu.sar.common.web.PublicRead;
import cn.edu.sar.domain.access.AccessService;
import cn.edu.sar.domain.access.SessionService;
import cn.edu.sar.web.access.AccessCommands.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccessController {
    private final AccessService access;
    private final SessionService sessions;
    private final IdempotencyService commands;
    private final ObjectAuthorization objects;
    public AccessController(AccessService access,SessionService sessions,IdempotencyService commands,ObjectAuthorization objects) {
        this.access=access;this.sessions=sessions;this.commands=commands;this.objects=objects;
    }
    private long actor(HttpServletRequest request) { return AccessInterceptor.current(request).id(); }
    private Object ok(Object data,HttpServletRequest request) { return ApiResponse.success(data,request); }
    private void cookie(HttpServletRequest request,HttpServletResponse response,String token) {
        var builder=ResponseCookie.from(SessionService.COOKIE,token==null?"":token).httpOnly(true).secure(request.isSecure()).path("/").sameSite("Lax");
        if(token==null) builder.maxAge(0);
        response.addHeader(HttpHeaders.SET_COOKIE,builder.build().toString());
    }
    @PublicRead @GetMapping("/csrf") public Object csrf(CsrfToken token,HttpServletRequest request) { return ok(Map.of("token",token.getToken(),"headerName",token.getHeaderName()),request); }
    @PostMapping("/accounts") public Object register(@RequestBody @Valid Register input,HttpServletRequest request) { return ok(access.register(input.account(),input.password(),input.nickname()),request); }
    @PostMapping("/sessions") public Object login(@RequestBody @Valid Login input,HttpServletRequest request,HttpServletResponse response) {
        var result=sessions.login(input.account(),input.password(),request.getRemoteAddr());
        sessions.logout(AccessInterceptor.token(request));cookie(request,response,result.token());
        if(request.getSession(false)!=null) request.changeSessionId();
        return ok(result.user(),request);
    }
    @DeleteMapping("/sessions/current") public Object logout(HttpServletRequest request,HttpServletResponse response) {
        sessions.logout(AccessInterceptor.token(request));cookie(request,response,null);return ok(Map.of("loggedOut",true),request);
    }
    @GetMapping("/accounts/me") public Object me(HttpServletRequest request) {
        long actor=actor(request); User user=access.me(actor);
        return ok(objects.project(actor,"ACCOUNT",user.id(),Map.of("id",user.id(),"account",user.account(),"nickname",user.nickname(),
            "contact",user.contact(),"roles",user.roles(),"volunteerState",user.volunteerState(),"version",user.version())),request);
    }
    @PatchMapping("/accounts/me") public Object profile(@RequestBody @Valid Profile input,HttpServletRequest request) {
        long actor=actor(request);
        var result=commands.execute(actor,"ACCOUNT.PROFILE.V1",input.requestKey(),
            Map.of("nickname",input.nickname(),"contact",input.contact(),"expectedVersion",input.expectedVersion()),User.class,
            ()->objects.require(actor,"ACCOUNT",Long.toString(actor),"UPDATE_PROFILE"),
            ()->access.profile(actor,input.nickname(),input.contact(),input.expectedVersion()));
        return ok(result,request);
    }
    @PostMapping("/accounts/me/password") public Object password(@RequestBody @Valid Password input,HttpServletRequest request,HttpServletResponse response) {
        access.password(actor(request),input.oldPassword(),input.newPassword(),input.expectedVersion(),RequestIdFilter.get(request));cookie(request,response,null);return ok(Map.of("changed",true),request);
    }
    @GetMapping("/accounts") public Object accounts(@RequestParam(defaultValue="") @Size(max=50) String keyword,@RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int pageSize,HttpServletRequest request) { return ok(access.accounts(actor(request),keyword,page,pageSize,false),request); }
    @GetMapping("/volunteers") public Object volunteers(@RequestParam(defaultValue="") @Size(max=50) String keyword,@RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int pageSize,HttpServletRequest request) { return ok(access.accounts(actor(request),keyword,page,pageSize,true),request); }
    @PostMapping("/accounts/{id}/roles") public Object roles(@PathVariable @Positive long id,@RequestBody @Valid Roles input,HttpServletRequest request) { return ok(access.roles(actor(request),id,input.roles(),input.expectedVersion(),input.reason(),RequestIdFilter.get(request)),request); }
    @PostMapping("/volunteer-requests") public Object apply(@RequestBody @Valid Apply input,HttpServletRequest request) { return ok(access.apply(actor(request),input.material(),input.requestKey(),RequestIdFilter.get(request)),request); }
    @GetMapping("/volunteer-requests") public Object applications(@RequestParam(defaultValue="false") boolean review,@RequestParam(defaultValue="") @Pattern(regexp="|PENDING|APPROVED|REJECTED") String state,@RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(50) int pageSize,HttpServletRequest request) { return ok(access.applications(actor(request),review,state,page,pageSize),request); }
    @PostMapping("/volunteer-requests/{id}/review") public Object review(@PathVariable @Positive long id,@RequestBody @Valid Review input,HttpServletRequest request) { return ok(access.review(actor(request),id,input.decision(),input.reason(),input.expectedVersion(),RequestIdFilter.get(request)),request); }
    @GetMapping("/volunteers/{id}/responsibilities") public Object responsibilities(@PathVariable @Positive long id,HttpServletRequest request) { return ok(access.responsibilities(actor(request),id),request); }
    @PostMapping("/volunteers/{id}/suspend") public Object suspend(@PathVariable @Positive long id,@RequestBody @Valid Suspend input,HttpServletRequest request) { return ok(access.suspend(actor(request),id,input.expectedVersion(),input.reason(),RequestIdFilter.get(request)),request); }
}
