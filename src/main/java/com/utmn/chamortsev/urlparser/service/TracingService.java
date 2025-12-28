package com.utmn.chamortsev.urlparser.service;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TracingService {

    private static final Logger logger = LoggerFactory.getLogger(TracingService.class);
    private final Map<String, TracingStats> tracingStats = new ConcurrentHashMap<>();

    private Tracer tracer;

    // Внедряем Tracer через конструктор
    @Autowired
    public TracingService(Tracer tracer) {
        this.tracer = tracer;
        logger.info("🔄 TracingService инициализирован с Tracer: {}", tracer);
    }

    //Создаем спан для парсинга URL
    public Span createUrlParsingSpan(String url, Long urlId, String method) {
        if (tracer == null) {
            logger.warn("Tracer не инициализирован, создаем NoOp span");
            return Span.getInvalid();
        }

        return tracer.spanBuilder("url.parsing")
                .setAttribute("http.url", url)
                .setAttribute("url.id", urlId)
                .setAttribute("processing.method", method)
                .setAttribute("span.type", "url-parsing")
                .startSpan();
    }


    //Создаем спан для HTTP запроса
    public Span createHttpRequestSpan(String url, String method) {
        if (tracer == null) {
            logger.warn("Tracer не инициализирован, создаем NoOp span");
            return Span.getInvalid();
        }

        return tracer.spanBuilder("http.request")
                .setAttribute("http.url", url)
                .setAttribute("http.method", method)
                .setAttribute("span.type", "http-request")
                .startSpan();
    }


    // Спан для извлечения контактов
    public Span createContactExtractionSpan(String contentHash) {
        if (tracer == null) {
            logger.warn("Tracer не инициализирован, создаем NoOp span");
            return Span.getInvalid();
        }

        return tracer.spanBuilder("contact.extraction")
                .setAttribute("content.hash", contentHash.substring(0, Math.min(16, contentHash.length())))
                .setAttribute("span.type", "contact-extraction")
                .startSpan();
    }


    // Спан для операций с БД
    public Span createDatabaseSpan(String operation, String table) {
        if (tracer == null) {
            logger.warn("Tracer не инициализирован, создаем NoOp span");
            return Span.getInvalid();
        }

        return tracer.spanBuilder("database.operation")
                .setAttribute("db.operation", operation)
                .setAttribute("db.table", table)
                .setAttribute("span.type", "database")
                .startSpan();
    }


    // Спан для операций с кэшем

    public Span createCacheSpan(String operation, String cacheName) {
        if (tracer == null) {
            logger.warn("Tracer не инициализирован, создаем NoOp span");
            return Span.getInvalid();
        }

        return tracer.spanBuilder("cache.operation")
                .setAttribute("cache.operation", operation)
                .setAttribute("cache.name", cacheName)
                .setAttribute("span.type", "cache")
                .startSpan();
    }


    // Обертка для выполнения операции с трейсингом
    public <T> T traceOperation(String operationName, String url, TracingOperation<T> operation) throws Exception {
        if (tracer == null) {
            logger.warn("Tracer не инициализирован, выполняется операция без трейсинга: {}", operationName);
            try {
                return operation.execute();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        Span span = createUrlParsingSpan(url, null, operationName);

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("operation.started");
            logger.debug("Начало операции {} для URL: {}", operationName, url);

            T result = operation.execute();

            span.addEvent("operation.completed");
            span.setStatus(StatusCode.OK);
            logger.debug("Операция {} завершена успешно для URL: {}", operationName, url);

            return result;

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.setAttribute("error.type", e.getClass().getSimpleName());
            logger.error("Ошибка операции {} для URL: {} - {}", operationName, url, e.getMessage());
            throw e;
        } finally {
            span.end();
            recordTracingStats(operationName, span.getSpanContext().isSampled());
        }
    }


    // Пишем статистику трейсинга
    private void recordTracingStats(String operationName, boolean sampled) {
        String key = operationName + "_" + System.currentTimeMillis() / 60000; // По минутам
        tracingStats.compute(key, (k, stats) -> {
            if (stats == null) {
                stats = new TracingStats();
            }
            stats.totalOperations++;
            if (sampled) {
                stats.sampledOperations++;
            }
            return stats;
        });
    }


    //Cтатистика трейсинга
    public Map<String, Object> getTracingStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalOps = tracingStats.values().stream()
                .mapToLong(s -> s.totalOperations)
                .sum();
        long sampledOps = tracingStats.values().stream()
                .mapToLong(s -> s.sampledOperations)
                .sum();

        stats.put("totalOperations", totalOps);
        stats.put("sampledOperations", sampledOps);
        stats.put("samplingRate", totalOps > 0 ?
                String.format("%.1f%%", sampledOps * 100.0 / totalOps) : "0%");
        stats.put("activeTraces", tracingStats.size());
        stats.put("tracingEnabled", tracer != null);
        stats.put("tracerName", tracer != null ? tracer.toString() : "NOT_INITIALIZED");

        return stats;
    }

    //Получаем текущий Trace ID
    public String getCurrentTraceId() {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getTraceId();
        }
        return null;
    }


     //Получает текущий Span ID
    public String getCurrentSpanId() {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getSpanId();
        }
        return null;
    }


    //Функциональный интерфейс для операций с трейсингом
    @FunctionalInterface
    public interface TracingOperation<T> {
        T execute() throws Exception;
    }

    //Внутренний класс для статистики трейсинга
    private static class TracingStats {
        long totalOperations = 0;
        long sampledOperations = 0;
    }
}
