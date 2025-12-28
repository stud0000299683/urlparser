package com.utmn.chamortsev.urlparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "otel")
@Data
public class OpenTelemetryProperties {
    private String serviceName = "url-parser-service";
    private String serviceVersion = "1.0.0";
    private Exporter exporter = new Exporter();

    @Data
    public static class Exporter {
        private Otlp otlp = new Otlp();

        @Data
        public static class Otlp {
            private String endpoint = "http://jaeger:4318/v1/traces";
            private String protocol = "http/protobuf";
        }
    }
}
