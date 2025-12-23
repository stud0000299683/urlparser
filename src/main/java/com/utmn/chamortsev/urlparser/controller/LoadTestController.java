package com.utmn.chamortsev.urlparser.controller;

import com.utmn.chamortsev.urlparser.dto.LoadTestRequest;
import com.utmn.chamortsev.urlparser.service.LoadTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/loadtest")
@CrossOrigin(origins = "*")
@Tag(name = "Load Testing API", description = "API для нагрузочного тестирования парсера URL")
public class LoadTestController {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestController.class);

    private final LoadTestService loadTestService;

    public LoadTestController(LoadTestService loadTestService) {
        this.loadTestService = loadTestService;
    }

    @Operation(
            summary = "Генерация тестовых URL",
            description = "Создает указанное количество тестовых URL для нагрузочного тестирования"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL успешно сгенерированы"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры")
    })
    @PostMapping("/generate-urls")
    public ResponseEntity<?> generateTestUrls(
            @Parameter(description = "Количество URL для генерации", example = "50")
            @RequestParam(defaultValue = "50") int count) {

        if (count <= 0 || count > 1000) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Количество URL должно быть от 1 до 1000"
            ));
        }

        try {
            var generatedUrls = loadTestService.generateTestUrls(count);

            return ResponseEntity.ok(Map.of(
                    "message", "Тестовые URL успешно сгенерированы",
                    "generatedCount", generatedUrls.size(),
                    "urls", generatedUrls.stream().map(url -> Map.of(
                            "id", url.getId(),
                            "url", url.getUrl(),
                            "name", url.getName()
                    )).toList(),
                    "timestamp", new java.util.Date()
            ));

        } catch (Exception e) {
            logger.error("Ошибка при генерации URL", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Ошибка при генерации URL: " + e.getMessage()
            ));
        }
    }

    @Operation(
            summary = "Запуск нагрузочного теста",
            description = "Запускает нагрузочное тестирование с указанными параметрами"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Тест успешно запущен"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры теста")
    })
    @PostMapping("/start")
    public ResponseEntity<?> startLoadTest(
            @Parameter(description = "Параметры нагрузочного теста")
            @RequestBody LoadTestRequest request) {

        try {
            // Валидация параметров
            if (request.getUrlCount() <= 0 || request.getUrlCount() > 1000) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Количество URL должно быть от 1 до 1000"
                ));
            }

            if (request.getThreadCount() <= 0 || request.getThreadCount() > 100) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Количество потоков должно быть от 1 до 100"
                ));
            }

            if (request.getDurationSeconds() <= 0 || request.getDurationSeconds() > 3600) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Длительность теста должна быть от 1 до 3600 секунд"
                ));
            }

            var result = loadTestService.startLoadTest(request);

            return ResponseEntity.accepted().body(result);

        } catch (Exception e) {
            logger.error("Ошибка при запуске нагрузочного теста", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Ошибка при запуске теста: " + e.getMessage()
            ));
        }
    }

    @Operation(
            summary = "Статус нагрузочного теста",
            description = "Получает текущий статус нагрузочного теста по ID"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус получен"),
            @ApiResponse(responseCode = "404", description = "Тест не найден")
    })
    @GetMapping("/status/{testId}")
    public ResponseEntity<?> getLoadTestStatus(
            @Parameter(description = "ID нагрузочного теста", example = "abc123")
            @PathVariable String testId) {

        try {
            var status = loadTestService.getLoadTestStatus(testId);

            if ("NOT_FOUND".equals(status.get("status"))) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            logger.error("Ошибка при получении статуса теста", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Ошибка при получении статуса: " + e.getMessage()
            ));
        }
    }

    @Operation(
            summary = "Список активных тестов",
            description = "Получает список всех активных нагрузочных тестов"
    )
    @GetMapping("/active")
    public ResponseEntity<?> getActiveLoadTests() {
        try {
            var activeTests = loadTestService.getActiveLoadTests();
            return ResponseEntity.ok(activeTests);

        } catch (Exception e) {
            logger.error("Ошибка при получении списка активных тестов", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Ошибка при получении списка тестов: " + e.getMessage()
            ));
        }
    }

    @Operation(
            summary = "Остановка всех тестов",
            description = "Останавливает все активные нагрузочные тесты"
    )
    @PostMapping("/stop-all")
    public ResponseEntity<?> stopAllLoadTests() {
        try {
            var result = loadTestService.stopAllLoadTests();
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Ошибка при остановке тестов", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Ошибка при остановке тестов: " + e.getMessage()
            ));
        }
    }

    @Operation(
            summary = "Быстрый старт теста",
            description = "Запускает нагрузочный тест с предустановленными параметрами"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Тест успешно запущен")
    })
    @PostMapping("/quick-start")
    public ResponseEntity<?> quickStartLoadTest(
            @Parameter(description = "Тип теста", example = "ASYNC")
            @RequestParam(defaultValue = "ASYNC") String testType) {

        try {
            LoadTestRequest request = new LoadTestRequest();
            request.setTestType(testType);
            request.setUrlCount(100);
            request.setThreadCount(20);
            request.setDurationSeconds(120);
            request.setRequestIntervalMs(50);
            request.setGenerateUrls(true);

            var result = loadTestService.startLoadTest(request);

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Быстрый тест запущен",
                    "testId", result.get("testId"),
                    "parameters", Map.of(
                            "testType", testType,
                            "urlCount", 100,
                            "threadCount", 20,
                            "durationSeconds", 120
                    ),
                    "timestamp", new java.util.Date()
            ));

        } catch (Exception e) {
            logger.error("Ошибка при быстром старте теста", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Ошибка при запуске теста: " + e.getMessage()
            ));
        }
    }
}