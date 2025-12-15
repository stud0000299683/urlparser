package com.utmn.chamortsev.urlparser.controller;

import com.utmn.chamortsev.urlparser.service.UrlProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/async")
@CrossOrigin(originPatterns = "*")
@Tag(name = "Async URL Processing API", description = "Асинхронные операции обработки URL")
public class AsyncUrlController {

    private final UrlProcessingService urlProcessingService;
    private final SimpMessagingTemplate messagingTemplate;  // ✅ WebSocket
    private static final Logger logger = LoggerFactory.getLogger(AsyncUrlController.class);

    public AsyncUrlController(UrlProcessingService urlProcessingService,
                              SimpMessagingTemplate messagingTemplate) {
        this.urlProcessingService = urlProcessingService;
        this.messagingTemplate = messagingTemplate;
    }

    @Operation(
            summary = "Асинхронная обработка всех URL",
            description = "Параллельная обработка URL с преобразованиями и объединением данных"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Асинхронная обработка запущена"
            )
    })
    @PostMapping("/process")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> processUrlsAsync() {
        logger.info("Запуск асинхронной обработки всех URL");

        return urlProcessingService.processAllUrlsAsync()
                .thenApply(results -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("message", "Асинхронная обработка завершена");
                    response.put("processedCount", results.size());
                    response.put("results", results);
                    response.put("timestamp", new Date());

                    long successCount = results.stream()
                            .filter(r -> Boolean.TRUE.equals(r.get("success")))
                            .count();
                    response.put("successCount", successCount);
                    response.put("successRate", results.isEmpty() ? "0%" :
                            String.format("%.1f%%", successCount * 100.0 / results.size()));

                    // отправка по WebSocket
                    for (Map<String, Object> result : results) {
                        Long urlId = (Long) result.get("urlId");
                        if (urlId != null) {
                            messagingTemplate.convertAndSend("/topic/url/" + urlId, result);
                        }
                    }

                    logger.info("Асинхронная обработка завершена, обработано {} URL, успешно: {}",
                            results.size(), successCount);

                    return ResponseEntity.ok(response);
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка асинхронной обработки", ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Ошибка асинхронной обработки: " + ex.getMessage()));
                });
    }


    @Operation(
            summary = "Получить расширенные результаты",
            description = "Возвращает детализированные результаты асинхронной обработки"
    )
    @GetMapping("/results/enhanced")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getEnhancedResults() {
        logger.info("Запрос расширенных результатов асинхронной обработки");

        return urlProcessingService.processAllUrlsAsync()
                .thenApply(results -> {
                    List<Map<String, Object>> enhancedResults = results.stream()
                            .map(this::enhanceResultWithAnalysis)
                            .collect(Collectors.toList());

                    Map<String, Object> response = Map.of(
                            "totalResults", enhancedResults.size(),
                            "results", enhancedResults,
                            "summary", generateSummary(enhancedResults),
                            "timestamp", new Date()
                    );

                    logger.info("Сформированы расширенные результаты для {} URL", enhancedResults.size());

                    return ResponseEntity.ok(response);
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка получения расширенных результатов", ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Ошибка получения расширенных результатов: " + ex.getMessage()));
                });
    }

    @Operation(
            summary = "Статус асинхронной обработки",
            description = "Возвращает текущий статус пула потоков и статистику обработки"
    )
    @GetMapping("/status")
    public CompletableFuture<ResponseEntity<?>> getAsyncStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> status = new HashMap<>();

                ThreadPoolExecutor executor = urlProcessingService.getThreadPoolExecutor();
                status.put("activeThreads", executor.getActiveCount());
                status.put("poolSize", executor.getPoolSize());
                status.put("queueSize", executor.getQueue().size());
                status.put("completedTasks", executor.getCompletedTaskCount());
                status.put("isShutdown", executor.isShutdown());

                Map<String, Object> stats = urlProcessingService.getStatistics();
                status.putAll(stats);

                status.put("timestamp", new Date());
                status.put("status", "RUNNING");

                logger.debug("Запрос статуса асинхронной обработки");

                return ResponseEntity.ok(status);

            } catch (Exception e) {
                logger.error("Ошибка получения статуса асинхронной обработки", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Ошибка получения статуса: " + e.getMessage()));
            }
        });
    }

    @Operation(
            summary = "ForkJoin обработка URL",
            description = "Рекурсивная обработка URL с разбиением на батчи через ForkJoin"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "ForkJoin обработка запущена"
            )
    })
    @PostMapping("/process/forkjoin")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> processUrlsWithForkJoin() {
        logger.info("Запуск ForkJoin обработки URL");

        return urlProcessingService.processUrlsWithForkJoin()
                .thenApply(result -> {
                    Map<String, Object> response = new HashMap<>(result);
                    response.put("message", "ForkJoin обработка завершена");
                    response.put("timestamp", new Date());

                    // ✅ WebSocket уведомления для каждого URL
                    if (result.containsKey("results")) {
                        List<Map<String, Object>> results = (List<Map<String, Object>>) result.get("results");
                        for (Map<String, Object> singleResult : results) {
                            Long urlId = (Long) singleResult.get("urlId");
                            if (urlId != null) {
                                messagingTemplate.convertAndSend("/topic/url/" + urlId, singleResult);
                            }
                        }
                    } else {
                        // Если результат содержит urlId напрямую (одиночный URL)
                        Long urlId = (Long) result.get("urlId");
                        if (urlId != null) {
                            messagingTemplate.convertAndSend("/topic/url/" + urlId, result);
                        }
                    }

                    // Добавляем детальную статистику
                    Map<String, Object> aggregatedStats = (Map<String, Object>) result.get("aggregatedStats");
                    if (aggregatedStats != null) {
                        response.put("detailedStats", aggregatedStats);
                    }

                    logger.info("ForkJoin обработка завершена. Обработано {} URL",
                            result.get("processedCount"));

                    return ResponseEntity.ok(response);
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка ForkJoin обработки", ex);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Ошибка ForkJoin обработки: " + ex.getMessage()));
                });
    }

    @Operation(
            summary = "Сравнение методов обработки",
            description = "Сравнивает производительность разных методов обработки"
    )
    @GetMapping("/compare-methods")
    public CompletableFuture<ResponseEntity<?>> compareProcessingMethods() {
        logger.info("Запуск сравнения методов обработки");

        CompletableFuture<Map<String, Object>> asyncFuture = urlProcessingService.processAllUrlsAsync()
                .thenApply(results -> Map.of(
                        "method", "COMPLETABLE_FUTURE",
                        "processedCount", results.size(),
                        "timestamp", new Date()
                ));

        CompletableFuture<Map<String, Object>> forkJoinFuture = urlProcessingService.processUrlsWithForkJoin()
                .thenApply(result -> Map.of(
                        "method", "FORK_JOIN",
                        "processedCount", result.get("processedCount"),
                        "aggregatedStats", result.get("aggregatedStats"),
                        "timestamp", new Date()
                ));

        return asyncFuture.thenCombine(forkJoinFuture, (asyncResult, forkJoinResult) -> {
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("asyncMethod", asyncResult);
            comparison.put("forkJoinMethod", forkJoinResult);
            comparison.put("comparisonTimestamp", new Date());

            logger.info("Сравнение методов обработки завершено");

            return ResponseEntity.ok(comparison);
        });
    }

    private Map<String, Object> enhanceResultWithAnalysis(Map<String, Object> result) {
        Map<String, Object> enhanced = new HashMap<>(result);

        Boolean success = (Boolean) result.get("success");
        if (success != null && success) {
            enhanced.put("analysis", "Успешный сбор данных");
            enhanced.put("recommendation", "Можно использовать для регулярного мониторинга");

            Long responseTime = (Long) result.get("responseTime");
            if (responseTime != null) {
                if (responseTime < 1000) {
                    enhanced.put("performanceNote", "Отличная скорость отклика");
                } else if (responseTime < 3000) {
                    enhanced.put("performanceNote", "Хорошая скорость отклика");
                } else {
                    enhanced.put("performanceNote", "Медленный отклик, требуется оптимизация");
                }
            }
        } else {
            enhanced.put("analysis", "Проблемы со сбором данных");
            enhanced.put("recommendation", "Требуется проверка доступности сайта");
        }

        int contactScore = calculateContactScore(result);
        enhanced.put("contactScore", contactScore);
        enhanced.put("contactQuality", contactScore >= 7 ? "HIGH" :
                contactScore >= 4 ? "MEDIUM" : "LOW");

        return enhanced;
    }

    private int calculateContactScore(Map<String, Object> result) {
        int score = 0;
        if (result.get("email") != null && !((String) result.get("email")).isEmpty()) score += 3;
        if (result.get("phone") != null && !((String) result.get("phone")).isEmpty()) score += 3;
        if (result.get("address") != null && !((String) result.get("address")).isEmpty()) score += 2;
        if (result.get("workingHours") != null && !((String) result.get("workingHours")).isEmpty()) score += 2;
        return score;
    }

    private Map<String, Object> generateSummary(List<Map<String, Object>> results) {
        Map<String, Object> summary = new HashMap<>();

        long total = results.size();
        long success = results.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count();
        long withContacts = results.stream().filter(r ->
                (r.get("email") != null && !((String) r.get("email")).isEmpty()) ||
                        (r.get("phone") != null && !((String) r.get("phone")).isEmpty())).count();

        OptionalDouble avgDataQuality = results.stream()
                .filter(r -> r.get("dataQualityScore") != null)
                .mapToDouble(r -> (Integer) r.get("dataQualityScore"))
                .average();

        OptionalDouble avgOverallScore = results.stream()
                .filter(r -> r.get("overallScore") != null)
                .mapToDouble(r -> (Double) r.get("overallScore"))
                .average();

        Map<String, Long> performanceDistribution = results.stream()
                .filter(r -> r.get("performance") != null)
                .collect(Collectors.groupingBy(
                        r -> (String) r.get("performance"),
                        Collectors.counting()
                ));

        summary.put("totalProcessed", total);
        summary.put("successful", success);
        summary.put("withContacts", withContacts);
        summary.put("successRate", total > 0 ? String.format("%.1f%%", success * 100.0 / total) : "0%");
        summary.put("contactExtractionRate", total > 0 ?
                String.format("%.1f%%", withContacts * 100.0 / total) : "0%");
        summary.put("averageDataQualityScore", avgDataQuality.isPresent() ?
                String.format("%.1f", avgDataQuality.getAsDouble()) : "N/A");
        summary.put("averageOverallScore", avgOverallScore.isPresent() ?
                String.format("%.2f", avgOverallScore.getAsDouble()) : "N/A");
        summary.put("performanceDistribution", performanceDistribution);

        return summary;
    }
}
