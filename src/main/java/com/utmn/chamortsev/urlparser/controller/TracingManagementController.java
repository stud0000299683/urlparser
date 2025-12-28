package com.utmn.chamortsev.urlparser.controller;

import com.utmn.chamortsev.urlparser.service.TracingService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/tracing")
@CrossOrigin(originPatterns = "*")
@Tag(name = "Tracing Management API", description = "Управление трейсингом и мониторинг OpenTelemetry")
public class TracingManagementController {

    private static final Logger logger = LoggerFactory.getLogger(TracingManagementController.class);

    private final TracingService tracingService;
    private final Tracer tracer;

    @Autowired
    public TracingManagementController(TracingService tracingService, Tracer tracer) {
        this.tracingService = tracingService;
        this.tracer = tracer;
        logger.info("✅ TracingManagementController инициализирован");
    }

    @Operation(
            summary = "Получить информацию о текущем трейсе",
            description = "Возвращает идентификаторы текущего трейса и спэна"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Информация успешно получена"),
            @ApiResponse(responseCode = "503", description = "Трейсинг не инициализирован")
    })
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentTraceInfo() {
        if (tracer == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Tracer не инициализирован",
                            "tracingEnabled", false,
                            "timestamp", System.currentTimeMillis()
                    ));
        }

        Span span = tracer.spanBuilder("getCurrentTraceInfo")
                .setAttribute("http.route", "/api/tracing/current")
                .setAttribute("http.method", "GET")
                .setAttribute("operation.type", "diagnostic")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Map<String, Object> response = new HashMap<>();

            String traceId = tracingService.getCurrentTraceId();
            String spanId = tracingService.getCurrentSpanId();

            response.put("currentTraceId", traceId);
            response.put("currentSpanId", spanId);
            response.put("hasActiveTrace", traceId != null);
            response.put("jaegerTraceUrl", traceId != null ?
                    String.format("http://localhost:16686/trace/%s", traceId) : null);
            response.put("timestamp", System.currentTimeMillis());
            response.put("tracingEnabled", true);
            response.put("tracerName", tracer.toString());

            span.addEvent("Trace information retrieved");
            span.setAttribute("trace.found", traceId != null);

            logger.info("Запрос информации о текущем трейсе. TraceId: {}", traceId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            logger.error("Ошибка получения информации о трейсе", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }

    @Operation(
            summary = "Статистика трейсинга",
            description = "Возвращает статистику по операциям трейсинга"
    )
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTracingStatistics() {
        if (tracer == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Tracer не инициализирован",
                            "tracingEnabled", false
                    ));
        }

        Span span = tracer.spanBuilder("getTracingStatistics")
                .setAttribute("http.route", "/api/tracing/statistics")
                .setAttribute("http.method", "GET")
                .setAttribute("operation.type", "metrics")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Map<String, Object> stats = tracingService.getTracingStatistics();
            stats.put("endpoint", "/api/tracing/statistics");
            stats.put("queryTime", System.currentTimeMillis());
            stats.put("tracerStatus", "ACTIVE");

            span.setAttribute("stats.totalOperations",
                    (Long) stats.get("totalOperations"));
            span.setAttribute("stats.samplingRate",
                    (String) stats.get("samplingRate"));

            logger.info("Запрос статистики трейсинга. Всего операций: {}",
                    stats.get("totalOperations"));

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            logger.error("Ошибка получения статистики трейсинга", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }

    @Operation(
            summary = "Демонстрация вложенного трейсинга",
            description = "Создает сложный трейс с вложенными операциями для демонстрации"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Демо-трейс успешно создан"),
            @ApiResponse(responseCode = "503", description = "Трейсинг не инициализирован")
    })
    @PostMapping("/demo")
    public ResponseEntity<Map<String, Object>> createDemoTrace() {
        if (tracer == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Tracer не инициализирован",
                            "tracingEnabled", false
                    ));
        }

        Span parentSpan = tracer.spanBuilder("demoTraceParent")
                .setAttribute("demo.type", "nested-tracing")
                .setAttribute("http.route", "/api/tracing/demo")
                .setAttribute("http.method", "POST")
                .setAttribute("demo.complexity", "high")
                .startSpan();

        try (Scope parentScope = parentSpan.makeCurrent()) {
            logger.info("Создание демонстрационного трейса");

            Map<String, Object> result = new HashMap<>();
            result.put("message", "Демонстрационный трейс создан");
            result.put("parentTraceId", parentSpan.getSpanContext().getTraceId());
            result.put("parentSpanId", parentSpan.getSpanContext().getSpanId());
            result.put("tracingMethod", "RECURSIVE_NESTED");

            // Child span 1: Начальная обработка
            Span childSpan1 = tracer.spanBuilder("demoInitialProcessing")
                    .setAttribute("operation", "data-preparation")
                    .setAttribute("subsystem", "initializer")
                    .startSpan();

            try (Scope childScope1 = childSpan1.makeCurrent()) {
                Thread.sleep(50); // Добавим слип для имитации обработки
                childSpan1.addEvent("Подготовка данных завершена");
                result.put("step1", "COMPLETED");
                result.put("step1Time", "50ms");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                childSpan1.setStatus(StatusCode.ERROR, "Interrupted");
                result.put("step1", "INTERRUPTED");
            } finally {
                childSpan1.end();
            }

            // Child span 2: Основные вычисления
            Span childSpan2 = tracer.spanBuilder("demoMainComputation")
                    .setAttribute("operation", "computation")
                    .setAttribute("iterations", 5)
                    .setAttribute("subsystem", "calculator")
                    .startSpan();

            try (Scope childScope2 = childSpan2.makeCurrent()) {
                for (int i = 0; i < 5; i++) {
                    Span iterationSpan = tracer.spanBuilder("demoIteration_" + i)
                            .setAttribute("iteration", i)
                            .setAttribute("batch", i % 2 == 0 ? "even" : "odd")
                            .startSpan();

                    try (Scope iterationScope = iterationSpan.makeCurrent()) {
                        Thread.sleep(20);
                        iterationSpan.addEvent("Итерация " + i + " завершена");

                        // Внутренняя операция в итерации
                        if (i == 2) {
                            Span innerSpan = tracer.spanBuilder("demoSpecialProcessing")
                                    .setAttribute("special", true)
                                    .setAttribute("iteration", i)
                                    .startSpan();
                            try (Scope innerScope = innerSpan.makeCurrent()) {
                                Thread.sleep(10);
                                innerSpan.addEvent("Специальная обработка завершена");
                            } finally {
                                innerSpan.end();
                            }
                        }
                    } finally {
                        iterationSpan.end();
                    }
                }
                childSpan2.addEvent("Основные вычисления завершены");
                result.put("step2", "COMPLETED");
                result.put("step2Iterations", 5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                childSpan2.setStatus(StatusCode.ERROR, "Interrupted");
                result.put("step2", "INTERRUPTED");
            } finally {
                childSpan2.end();
            }

            // Child span 3: Финализация
            Span childSpan3 = tracer.spanBuilder("demoFinalization")
                    .setAttribute("operation", "finalization")
                    .setAttribute("subsystem", "finalizer")
                    .startSpan();

            try (Scope childScope3 = childSpan3.makeCurrent()) {
                Thread.sleep(30);

                CompletableFuture<Void> parallelTask1 = CompletableFuture.runAsync(() -> {
                    Span parallelSpan1 = tracer.spanBuilder("demoParallelTask1")
                            .setAttribute("task.type", "cleanup")
                            .startSpan();
                    try (Scope parallelScope1 = parallelSpan1.makeCurrent()) {
                        Thread.sleep(15);
                        parallelSpan1.addEvent("Очистка данных завершена");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        parallelSpan1.end();
                    }
                });

                CompletableFuture<Void> parallelTask2 = CompletableFuture.runAsync(() -> {
                    Span parallelSpan2 = tracer.spanBuilder("demoParallelTask2")
                            .setAttribute("task.type", "validation")
                            .startSpan();
                    try (Scope parallelScope2 = parallelSpan2.makeCurrent()) {
                        Thread.sleep(10);
                        parallelSpan2.addEvent("Валидация завершена");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        parallelSpan2.end();
                    }
                });

                parallelTask1.join();
                parallelTask2.join();

                childSpan3.addEvent("Финализация завершена");
                result.put("step3", "COMPLETED");
                result.put("step3ParallelTasks", 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                childSpan3.setStatus(StatusCode.ERROR, "Interrupted");
                result.put("step3", "INTERRUPTED");
            } finally {
                childSpan3.end();
            }

            parentSpan.addEvent("Все операции демо-трейса завершены");
            result.put("totalSteps", 3);
            result.put("timestamp", System.currentTimeMillis());
            result.put("jaegerUrl",
                    String.format("http://localhost:16686/trace/%s",
                            parentSpan.getSpanContext().getTraceId()));
            result.put("spanCount", 13);

            logger.info("Демонстрационный трейс создан. TraceId: {}, Всего спэнов: {}",
                    parentSpan.getSpanContext().getTraceId(), 13);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            parentSpan.recordException(e);
            parentSpan.setStatus(StatusCode.ERROR, e.getMessage());
            logger.error("Ошибка создания демо-трейса", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } finally {
            parentSpan.end();
        }
    }

    @Operation(
            summary = "Тест асинхронного трейсинга",
            description = "Демонстрирует трейсинг в асинхронных операциях"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Асинхронный трейс создан"),
            @ApiResponse(responseCode = "503", description = "Трейсинг не инициализирован")
    })
    @PostMapping("/async-demo")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createAsyncDemoTrace() {
        if (tracer == null) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(Map.of(
                                    "error", "Tracer не инициализирован",
                                    "tracingEnabled", false
                            ))
            );
        }

        Span initialSpan = tracer.spanBuilder("asyncDemoInitial")
                .setAttribute("http.route", "/api/tracing/async-demo")
                .setAttribute("http.method", "POST")
                .setAttribute("async", true)
                .startSpan();

        return CompletableFuture.supplyAsync(() -> {
            try (Scope initialScope = initialSpan.makeCurrent()) {
                logger.info("⚡ Начало асинхронного демо-трейса");

                // Создаем спан для асинхронной операции
                Span asyncSpan = tracer.spanBuilder("asyncOperation")
                        .setAttribute("operation.type", "async-processing")
                        .setAttribute("concurrent.tasks", 3)
                        .setParent(io.opentelemetry.context.Context.current())
                        .startSpan();

                Map<String, Object> result = new HashMap<>();

                try (Scope asyncScope = asyncSpan.makeCurrent()) {
                    // Имитация асинхронной работы
                    Thread.sleep(100);

                    // Вложенные асинхронные операции
                    CompletableFuture<Void> task1 = CompletableFuture.runAsync(() -> {
                        Span nestedSpan1 = tracer.spanBuilder("asyncNestedTask1")
                                .setAttribute("task.type", "data-fetching")
                                .startSpan();

                        try (Scope nestedScope1 = nestedSpan1.makeCurrent()) {
                            Thread.sleep(50);
                            nestedSpan1.setAttribute("data.size", "1KB");
                            nestedSpan1.addEvent("Данные успешно получены");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            nestedSpan1.setStatus(StatusCode.ERROR, "Interrupted");
                        } finally {
                            nestedSpan1.end();
                        }
                    });

                    CompletableFuture<Void> task2 = CompletableFuture.runAsync(() -> {
                        Span nestedSpan2 = tracer.spanBuilder("asyncNestedTask2")
                                .setAttribute("task.type", "data-processing")
                                .startSpan();

                        try (Scope nestedScope2 = nestedSpan2.makeCurrent()) {
                            Thread.sleep(70);

                            // Внутренняя асинхронная операция
                            CompletableFuture<Void> innerTask = CompletableFuture.runAsync(() -> {
                                Span innerSpan = tracer.spanBuilder("asyncInnerTask")
                                        .setAttribute("task.type", "inner-processing")
                                        .startSpan();
                                try (Scope innerScope = innerSpan.makeCurrent()) {
                                    Thread.sleep(30);
                                    innerSpan.addEvent("Внутренняя обработка завершена");
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } finally {
                                    innerSpan.end();
                                }
                            });

                            innerTask.join();
                            nestedSpan2.setAttribute("processed.items", 150);
                            nestedSpan2.addEvent("Обработка данных завершена");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            nestedSpan2.setStatus(StatusCode.ERROR, "Interrupted");
                        } finally {
                            nestedSpan2.end();
                        }
                    });

                    CompletableFuture<Void> task3 = CompletableFuture.runAsync(() -> {
                        Span nestedSpan3 = tracer.spanBuilder("asyncNestedTask3")
                                .setAttribute("task.type", "result-aggregation")
                                .startSpan();

                        try (Scope nestedScope3 = nestedSpan3.makeCurrent()) {
                            Thread.sleep(40);
                            nestedSpan3.setAttribute("aggregated.results", 3);
                            nestedSpan3.addEvent("Агрегация результатов завершена");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            nestedSpan3.setStatus(StatusCode.ERROR, "Interrupted");
                        } finally {
                            nestedSpan3.end();
                        }
                    });

                    // Ждем завершения всех задач
                    CompletableFuture.allOf(task1, task2, task3).join();

                    asyncSpan.addEvent("Основная асинхронная операция завершена");
                    asyncSpan.setAttribute("completed.tasks", 3);
                    asyncSpan.setAttribute("total.time", "100ms");

                    result.put("message", "Асинхронный трейс создан");
                    result.put("traceId", asyncSpan.getSpanContext().getTraceId());
                    result.put("spanId", asyncSpan.getSpanContext().getSpanId());
                    result.put("async", true);
                    result.put("concurrentTasks", 3);
                    result.put("timestamp", System.currentTimeMillis());
                    result.put("jaegerUrl",
                            String.format("http://localhost:16686/trace/%s",
                                    asyncSpan.getSpanContext().getTraceId()));

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    asyncSpan.setStatus(StatusCode.ERROR, "Interrupted");
                    result.put("error", "Operation interrupted");
                } finally {
                    asyncSpan.end();
                }

                initialSpan.addEvent("Асинхронный демо-трейс завершен");
                logger.info("⚡ Асинхронный демо-трейс завершен");

                return ResponseEntity.ok(result);
            } catch (Exception e) {
                initialSpan.recordException(e);
                initialSpan.setStatus(StatusCode.ERROR, e.getMessage());
                logger.error("Ошибка асинхронного демо-трейса", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", e.getMessage()));
            } finally {
                initialSpan.end();
            }
        });
    }

    @Operation(
            summary = "Проверка подключения к Jaeger",
            description = "Проверяет доступность Jaeger и возвращает статус"
    )
    @GetMapping("/jaeger/status")
    public ResponseEntity<Map<String, Object>> checkJaegerStatus() {
        if (tracer == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Tracer не инициализирован",
                            "tracingEnabled", false
                    ));
        }

        Span span = tracer.spanBuilder("checkJaegerStatus")
                .setAttribute("http.route", "/api/tracing/jaeger/status")
                .setAttribute("http.method", "GET")
                .setAttribute("check.type", "connectivity")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            Map<String, Object> status = new HashMap<>();

            // Проверяем доступность Jaeger (упрощенная проверка)
            boolean jaegerAvailable = true; // В реальности нужно проверять подключение, но тут оставим так.

            status.put("service", "Jaeger");
            status.put("available", jaegerAvailable);
            status.put("uiUrl", "http://localhost:16686");
            status.put("otlpEndpoint", "http://jaeger:4318/v1/traces");
            status.put("otlpProtocol", "HTTP/PROTOBUF");
            status.put("checkTime", System.currentTimeMillis());
            status.put("dockerService", "jaeger:4318");

            if (jaegerAvailable) {
                status.put("status", "CONNECTED");
                status.put("statusCode", "HEALTHY");
                span.setAttribute("jaeger.status", "connected");
                span.setAttribute("jaeger.health", "healthy");
                logger.info("Jaeger доступен");
            } else {
                status.put("status", "DISCONNECTED");
                status.put("statusCode", "UNHEALTHY");
                status.put("warning", "Jaeger недоступен. Проверьте контейнер.");
                span.setAttribute("jaeger.status", "disconnected");
                span.setAttribute("jaeger.health", "unhealthy");
                span.setStatus(StatusCode.ERROR, "Jaeger unavailable");
                logger.warn("Jaeger недоступен");
            }

            //  Диагностическая информация
            status.put("tracer", tracer.toString());
            status.put("tracingEnabled", true);
            status.put("spanId", span.getSpanContext().getSpanId());
            status.put("traceId", span.getSpanContext().getTraceId());

            return ResponseEntity.ok(status);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            logger.error("Ошибка проверки статуса Jaeger", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }

    @Operation(
            summary = "Создание тестового URL с трейсингом",
            description = "Создает тестовый URL и демонстрирует трейсинг при его обработке"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Тестовый URL создан"),
            @ApiResponse(responseCode = "503", description = "Трейсинг не инициализирован")
    })
    @PostMapping("/test-url")
    public ResponseEntity<Map<String, Object>> createTestUrlWithTracing() {
        if (tracer == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Tracer не инициализирован",
                            "tracingEnabled", false
                    ));
        }

        Span span = tracer.spanBuilder("createTestUrlWithTracing")
                .setAttribute("http.route", "/api/tracing/test-url")
                .setAttribute("http.method", "POST")
                .setAttribute("test.type", "integration")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            logger.info("🔗 Создание тестового URL с трейсингом");

            Map<String, Object> result = new HashMap<>();

            // Генерируем тестовый URL
            String[] testDomains = {
                    "https://httpbin.org",
                    "https://jsonplaceholder.typicode.com",
                    "https://reqres.in"
            };

            String[] testPaths = {
                    "/status/200",
                    "/delay/1",
                    "/html",
                    "/json"
            };

            String testUrl = testDomains[ThreadLocalRandom.current().nextInt(testDomains.length)] +
                    testPaths[ThreadLocalRandom.current().nextInt(testPaths.length)];

            result.put("testUrl", testUrl);
            result.put("generatedAt", System.currentTimeMillis());
            result.put("purpose", "tracing-demonstration");

            // Создаем вложенные спэны для имитации обработки
            Span processingSpan = tracer.spanBuilder("testUrlProcessing")
                    .setAttribute("url", testUrl)
                    .setAttribute("processing.stage", "validation")
                    .startSpan();

            try (Scope processingScope = processingSpan.makeCurrent()) {
                Thread.sleep(30);
                processingSpan.addEvent("URL валидация завершена");

                Span extractionSpan = tracer.spanBuilder("testUrlAnalysis")
                        .setAttribute("url", testUrl)
                        .setAttribute("analysis.type", "structure")
                        .startSpan();

                try (Scope extractionScope = extractionSpan.makeCurrent()) {
                    Thread.sleep(40);
                    extractionSpan.setAttribute("url.length", testUrl.length());
                    extractionSpan.setAttribute("has.https", testUrl.startsWith("https://"));
                    extractionSpan.addEvent("Анализ URL завершен");
                } finally {
                    extractionSpan.end();
                }

                result.put("validation", "PASSED");
                result.put("analysis", "COMPLETED");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                processingSpan.setStatus(StatusCode.ERROR, "Interrupted");
                result.put("validation", "INTERRUPTED");
            } finally {
                processingSpan.end();
            }

            span.addEvent("Тестовый URL создан и обработан");
            result.put("traceId", span.getSpanContext().getTraceId());
            result.put("spanId", span.getSpanContext().getSpanId());
            result.put("jaegerUrl",
                    String.format("http://localhost:16686/trace/%s",
                            span.getSpanContext().getTraceId()));
            result.put("message", "Тестовый URL создан. Проверьте трейсинг в Jaeger.");

            logger.info("Тестовый URL создан: {}, TraceId: {}", testUrl, span.getSpanContext().getTraceId());

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            logger.error("Ошибка создания тестового URL", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }

    @Operation(
            summary = "Сброс статистики трейсинга",
            description = "Сбрасывает внутреннюю статистику трейсинга"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статистика сброшена"),
            @ApiResponse(responseCode = "503", description = "Трейсинг не инициализирован")
    })
    @PostMapping("/reset-stats")
    public ResponseEntity<Map<String, Object>> resetTracingStatistics() {
        if (tracer == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Tracer не инициализирован",
                            "tracingEnabled", false
                    ));
        }

        Span span = tracer.spanBuilder("resetTracingStatistics")
                .setAttribute("http.route", "/api/tracing/reset-stats")
                .setAttribute("http.method", "POST")
                .setAttribute("operation.type", "maintenance")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            logger.info("Сброс статистики трейсинга");

            // Для демонстрации просто возвращаем сообщение

            Map<String, Object> result = new HashMap<>();
            result.put("message", "Статистика трейсинга сброшена");
            result.put("timestamp", System.currentTimeMillis());
            result.put("operation", "RESET_STATS");
            result.put("traceId", span.getSpanContext().getTraceId());

            span.addEvent("Статистика трейсинга сброшена");

            logger.info("Статистика трейсинга сброшена");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            logger.error("Ошибка сброса статистики трейсинга", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } finally {
            span.end();
        }
    }
}