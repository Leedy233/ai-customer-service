package com.example.aicustomerservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final LoginInterceptor loginInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/ai/**");
        registry.addInterceptor(loginInterceptor).addPathPatterns("/ai/**")
                .excludePathPatterns("/ai/login", "/ai/logout");
        registry.addInterceptor(adminInterceptor).addPathPatterns("/ai/admin/**");
    }
}