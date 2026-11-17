package cn.edu.sar.common.config;

import cn.edu.sar.common.web.AccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    private final AccessInterceptor interceptor;
    public WebConfiguration(AccessInterceptor interceptor) { this.interceptor=interceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(interceptor).addPathPatterns("/api/**"); }
}
