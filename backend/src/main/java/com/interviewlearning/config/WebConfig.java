package com.interviewlearning.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Vite dev server (15173) and the preview server that exercises the
 * service worker (4173) to call the API during local development.
 *
 * A packaged run serves the frontend same-origin, so nothing here applies to it
 * — PROVIDED the app can tell that it is the same origin. Behind a proxy that
 * terminates TLS it cannot, unless server.forward-headers-strategy is set; that
 * combination once rejected every write the phone made.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:15173", "http://127.0.0.1:15173",
                        "http://localhost:4173", "http://127.0.0.1:4173")
                .allowedMethods("GET", "POST", "OPTIONS");
    }

    /**
     * Tomcat has no mapping for `.webmanifest`, so the packaged run served the
     * PWA manifest as application/octet-stream — which browsers may refuse to
     * parse, and an unparsed manifest means no install prompt on the phone.
     */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> webManifestMimeType() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
