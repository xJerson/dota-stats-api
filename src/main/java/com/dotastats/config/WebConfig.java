package com.dotastats.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS para la interfaz web (ver {@code dotastats.web.cors-origins}). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] origenes;

    public WebConfig(@Value("${dotastats.web.cors-origins:http://localhost:5173}") String[] origenes) {
        this.origenes = origenes;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origenes)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*");
    }
}
