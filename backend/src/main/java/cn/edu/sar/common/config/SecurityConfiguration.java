package cn.edu.sar.common.config;

import cn.edu.sar.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfiguration {
    @Bean public PasswordEncoder passwordEncoder() { return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
    @Bean public SecurityFilterChain security(HttpSecurity http,ObjectMapper json) throws Exception {
        var csrf=new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).sameSite("Lax").path("/"));
        http.csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .authorizeHttpRequests(config -> config.anyRequest().permitAll())
            .formLogin(config -> config.disable()).httpBasic(config -> config.disable())
            .logout(config -> config.disable())
            .exceptionHandling(config -> config.accessDeniedHandler((request,response,error) -> {
                response.setStatus(403);response.setContentType(MediaType.APPLICATION_JSON_VALUE);response.setCharacterEncoding("UTF-8");
                json.writeValue(response.getWriter(),ApiResponse.failure("CSRF_INVALID","页面验证已过期，请刷新后重试",null,request));
            }));
        return http.build();
    }
}
