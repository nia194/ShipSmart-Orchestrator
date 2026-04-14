package com.shipsmart.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
public class AppConfig {

    @Value("${shipsmart.cors.allowed-origins:http://localhost:5173}")
    private String allowedOriginsRaw;

    /**
     * CORS configuration.
     * Allowed origins come from the CORS_ALLOWED_ORIGINS environment variable
     * (comma-separated). Add your Render frontend URL in production.
     * Wired into Spring Security via SecurityConfig.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .toList();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * RestTemplate bean configured with reasonable timeouts for external API calls.
     * Used by shipping providers (FedEx, etc.) to call carrier APIs.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(20))
                .build();
    }

    // TODO: Add WebClient bean here for calling the FastAPI Python service
    // @Bean
    // public WebClient pythonApiClient(@Value("${shipsmart.python-api.base-url}") String baseUrl) {
    //     return WebClient.builder().baseUrl(baseUrl).build();
    // }
}
