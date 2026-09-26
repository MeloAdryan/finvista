package com.finvista.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

        @Value("${finvista.cors.allowed-origins}")
        private String allowedOrigins;

        @Override
        public void addCorsMappings(CorsRegistry registry) {

                String[] origins = Arrays.stream(
                                allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toArray(String[]::new);

                registry.addMapping("/api/**")
                                .allowedOrigins(origins)
                                .allowedMethods(
                                                "GET",
                                                "POST",
                                                "PUT",
                                                "PATCH",
                                                "DELETE",
                                                "OPTIONS")
                                .allowedHeaders("*")
                                .allowCredentials(true)
                                .maxAge(3600);
        }
}