package com.exam.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许的跨源，来自配置 app.security.allowed-origins（逗号分隔，与 AuthInterceptor 同一来源）。 */
    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.security.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
                      String allowedOrigins) {
        List<String> list = allowedOrigins == null || allowedOrigins.isBlank()
                ? List.of()
                : Arrays.stream(allowedOrigins.split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).collect(Collectors.toList());
        this.allowedOrigins = list.toArray(new String[0]);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
