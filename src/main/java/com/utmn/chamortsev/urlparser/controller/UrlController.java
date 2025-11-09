package com.utmn.chamortsev.urlparser.controller;


import com.utmn.chamortsev.urlparser.dto.UrlUpdateRequest;
import com.utmn.chamortsev.urlparser.entity.UrlEntity;
import com.utmn.chamortsev.urlparser.entity.UrlResultEntity;
import com.utmn.chamortsev.urlparser.repository.UrlRepository;
import com.utmn.chamortsev.urlparser.repository.UrlResultRepository;
import com.utmn.chamortsev.urlparser.service.UrlProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/api/urls")
@CrossOrigin(origins = "*")
@Tag(name = "URL Parser API", description = "API для сбора информации по контактам с сайтов")
public class UrlController {

    private final UrlRepository urlRepository;
    private final UrlResultRepository urlResultRepository;
    private final UrlProcessingService urlProcessingService;

    public UrlController(
            UrlRepository urlRepository,
            UrlResultRepository urlResultRepository,
            UrlProcessingService urlProcessingService) {
        this.urlRepository = urlRepository;
        this.urlResultRepository = urlResultRepository;
        this.urlProcessingService = urlProcessingService;
    }

    @Operation(
            summary = "Добавить один URL",
            description = "Добавить новый URL в базу данных для последующей обработки"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL добавлен успешно"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Некорректный URL или URL уже существует"
            )
    })
    @PostMapping
    public ResponseEntity<?> addUrl(
            @Parameter(description = "Данные URL", required = true)
            @RequestBody @Valid UrlRequest urlRequest) {
        try {
            if (urlRequest.getUrl() == null || urlRequest.getUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "URL обязателен"));
            }

            UrlEntity savedUrl = urlProcessingService.addUrl(
                    urlRequest.getUrl(),
                    urlRequest.getName(),
                    urlRequest.getDescription()
            );
            return ResponseEntity.ok(Map.of(
                    "message", "URL добавлен успешно",
                    "url", savedUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Внутренняя ошибка сервера"));
        }
    }

    @Operation(
            summary = "Обновить URL",
            description = "Обновить информацию URL (название, описание, активность)"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL обновлен успешно"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "URL не найден"
            )
    })
    @PutMapping("/{urlId}")
    public ResponseEntity<?> updateUrl(
            @Parameter(description = "ID URL для обновления", example = "1")
            @PathVariable Long urlId,
            @Parameter(description = "Поля для обновления", required = true)
            @RequestBody @Valid UrlUpdateRequest updateRequest) {
        try {
            UrlEntity url = urlRepository.findById(urlId)
                    .orElseThrow(() -> new IllegalArgumentException("URL не найден"));

            if (updateRequest.getName() != null) {
                url.setName(updateRequest.getName());
            }
            if (updateRequest.getDescription() != null) {
                url.setDescription(updateRequest.getDescription());
            }
            if (updateRequest.getActive() != null) {
                url.setActive(updateRequest.getActive());
            }

            UrlEntity updatedUrl = urlRepository.save(url);
            return ResponseEntity.ok(Map.of(
                    "message", "URL обновлен успешно",
                    "url", updatedUrl
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    @Operation(
            summary = "Добавление нескольких сайтов (URL)",
            description = "Позволяет добавить несколько сайтов"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URLs успешно добавлены"
            )
    })
    @PostMapping("/batch")
    public ResponseEntity<?> addUrls(
            @Parameter(
                    description = "Список URL для добавления",
                    required = true
            )
            @RequestBody List<Map<String, String>> urls) {
        try {
            List<UrlEntity> savedUrls = urlProcessingService.addUrls(urls);
            return ResponseEntity.ok(Map.of(
                    "message", "URLs успешно добавлены",
                    "addedCount", savedUrls.size(),
                    "urls", savedUrls
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Обработать все ссылки URLs",
            description = "Метод запускающий процедуру парсинга данных по активным ссылкам"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Проесс успешно стартовал"
            )
    })
    @PostMapping("/process")
    public ResponseEntity<?> processUrls() {
        try {
            CompletableFuture<Map<String, Object>> future = urlProcessingService.processAllUrls();

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Сбор данных стартовал",
                    "status", "PROCESSING",
                    "timestamp", new Date()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(
            summary = "Получить все URL записи из базы ",
            description = "Выводит все что есть в базе"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Список получен",
                    content = @Content(schema = @Schema(implementation = UrlEntity[].class))
            )
    })
    @GetMapping
    public List<UrlEntity> getAllUrls() {
        return urlRepository.findAllByOrderByCreatedAtDesc();
    }

    @Operation(
            summary = "Получить действующие записи",
            description = "Возвращает только активные записи"
    )
    @GetMapping("/active")
    public List<UrlEntity> getActiveUrls() {
        return urlRepository.findByActiveTrueOrderByCreatedAtDesc();
    }

    @Operation(
            summary = "Получить обработанные контакты из базы",
            description = "Возвращает все записи из базы"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Успех",
                    content = @Content(schema = @Schema(implementation = UrlResultEntity[].class))
            )
    })
    @GetMapping("/results")
    public List<UrlResultEntity> getAllResults() {
        return urlResultRepository.findActiveUrlResults();
    }

    @Operation(
            summary = "Получить финальный результат собранных данных",
            description = "Возвращает данные которые удалось собрать с сайтов"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Данные успешно выведены"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "URL не найден"
            )
    })
    @GetMapping("/{urlId}/results")
    public ResponseEntity<?> getUrlResults(
            @Parameter(description = "ID записи URL (ссылки)", example = "1")
            @PathVariable Long urlId) {
        try {
            List<UrlResultEntity> results = urlResultRepository.findByUrlEntityIdOrderByProcessedAtDesc(urlId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Получить статистику сбора",
            description = "Возвращает информацию по процессу сбора"
    )
    @GetMapping("/statistics")
    public Map<String, Object> getStatistics() {
        return urlProcessingService.getStatistics();
    }

    @Operation(
            summary = "Возвращает информацию по потокам",
            description = "Возвращает  информацию о пуле потоков обработки"
    )
    @GetMapping("/thread-pool-info")
    public Map<String, Object> getThreadPoolInfo() {
        ThreadPoolExecutor executor = urlProcessingService.getThreadPoolExecutor();

        Map<String, Object> info = new HashMap<>();
        info.put("activeThreads", executor.getActiveCount());
        info.put("poolSize", executor.getPoolSize());
        info.put("corePoolSize", executor.getCorePoolSize());
        info.put("maximumPoolSize", executor.getMaximumPoolSize());
        info.put("queueSize", executor.getQueue().size());
        info.put("completedTaskCount", executor.getCompletedTaskCount());
        info.put("taskCount", executor.getTaskCount());
        info.put("isShutdown", executor.isShutdown());
        info.put("isTerminated", executor.isTerminated());

        return info;
    }
    @Operation(
            summary = "Удалить URL",
            description = "Удаляет URL из базы"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL успешно удален"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "URL не найден"
            )
    })
    @DeleteMapping("/{urlId}")
    public ResponseEntity<?> deleteUrl(
            @Parameter(description = "ID записи для удаления", example = "1")
            @PathVariable Long urlId) {
        try {
            if (!urlRepository.existsById(urlId)) {
                return ResponseEntity.notFound().build();
            }
            urlRepository.deleteById(urlId);
            return ResponseEntity.ok(Map.of("message", "URL успешно удален"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Getter
    @Setter
    @Schema(description = "URL request model")
    static class UrlRequest {
        @Schema(description = "URL (ссылка на страницу)", example = "https://example.com", required = true)
        private String url;

        @Schema(description = "Отображаемое название", example = "My Website")
        private String name;

        @Schema(description = "Описание", example = "Просто краткое описание сайта")
        private String description;

//        // Getters and Setters
//        public String getUrl() { return url; }
//        public void setUrl(String url) { this.url = url; }
//
//        public String getName() { return name; }
//        public void setName(String name) { this.name = name; }
//
//        public String getDescription() { return description; }
//        public void setDescription(String description) { this.description = description; }
    }
}