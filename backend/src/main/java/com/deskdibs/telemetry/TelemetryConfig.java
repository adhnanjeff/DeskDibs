package com.deskdibs.telemetry;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link ApiTelemetryInterceptor} against the API surface only.
 *
 * <p>Scoped to {@code /api/**} on purpose. Health probes run on a short timer and would drown the
 * admin view in traffic nobody asked about, and the OpenAPI documents are static. Neither says
 * anything about what the office is doing right now, which is the only question this view answers.
 */
@Configuration(proxyBeanMethods = false)
public class TelemetryConfig implements WebMvcConfigurer {

    private final ApiTelemetryInterceptor telemetryInterceptor;

    public TelemetryConfig(ApiTelemetryInterceptor telemetryInterceptor) {
        this.telemetryInterceptor = telemetryInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(telemetryInterceptor).addPathPatterns("/api/**");
    }
}
