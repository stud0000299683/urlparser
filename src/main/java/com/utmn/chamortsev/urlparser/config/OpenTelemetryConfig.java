package com.utmn.chamortsev.urlparser.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class OpenTelemetryConfig {

    @Value("${spring.application.name:url-parser-service}")
    private String serviceName;

    @Value("${spring.application.version:1.0.0}")
    private String serviceVersion;

    @Bean
    public OpenTelemetry openTelemetry() {
        log.info("Инициализация OpenTelemetry");

        Resource resource = Resource.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName)
                .put(ResourceAttributes.SERVICE_VERSION, serviceVersion)
                .put(ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "docker")
                .build();

        // Создаем SdkTracerProvider без экспортера, трейсы будут создаваться, но никуда их не отправляем
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .build();

        OpenTelemetrySdk openTelemetrySdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(
                        io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance()
                ))
                .buildAndRegisterGlobal();

        log.info("OpenTelemetry инициализирован");
        log.info("Трейсы создаются, но не экспортируются (режим разработки)");

        return openTelemetrySdk;
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName, serviceVersion);
    }
}