package cn.edu.sar.common.web;

import cn.edu.sar.domain.access.AccessModels.Account;
import cn.edu.sar.domain.access.SessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AccessInterceptor implements HandlerInterceptor {
    private final SessionService sessions;
    public AccessInterceptor(SessionService sessions) { this.sessions=sessions; }
    public static String token(HttpServletRequest request) {
        if (request.getCookies()!=null) for (Cookie cookie:request.getCookies()) if (cookie.getName().equals(SessionService.COOKIE)) return cookie.getValue();
        return null;
    }
    public static Account current(HttpServletRequest request) {
        Object actor=request.getAttribute("sar.actor");
        if (!(actor instanceof Account)) throw SessionService.unauthorized();
        return (Account)actor;
    }
    @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
        if (!(handler instanceof HandlerMethod endpoint) || !request.getRequestURI().startsWith("/api/")) return true;
        String path=request.getRequestURI(),method=request.getMethod();
        if (((method.equals("GET") || method.equals("HEAD")) && endpoint.hasMethodAnnotation(PublicRead.class))
            || (method.equals("POST") && (path.equals("/api/v1/accounts") || path.equals("/api/v1/sessions")))) return true;
        request.setAttribute("sar.actor",sessions.authenticate(token(request)));
        return true;
    }
}
