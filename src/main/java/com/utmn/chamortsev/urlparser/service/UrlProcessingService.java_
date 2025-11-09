package com.utmn.chamortsev.urlparser.service;

import com.utmn.chamortsev.urlparser.entity.UrlEntity;
import com.utmn.chamortsev.urlparser.entity.UrlResultEntity;
import com.utmn.chamortsev.urlparser.repository.UrlRepository;
import com.utmn.chamortsev.urlparser.repository.UrlResultRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.concurrent.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class UrlProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(UrlProcessingService.class);

    private final UrlRepository urlRepository;
    private final UrlResultRepository urlResultRepository;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final HttpClient httpClient;

    private static final int THREAD_POOL_SIZE = 5;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    public UrlProcessingService(UrlRepository urlRepository, UrlResultRepository urlResultRepository) {
        this.urlRepository = urlRepository;
        this.urlResultRepository = urlResultRepository;

        this.threadPoolExecutor = new ThreadPoolExecutor(
                THREAD_POOL_SIZE,
                THREAD_POOL_SIZE,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        logger.info("ThreadPoolExecutor запущен с {} потоками", THREAD_POOL_SIZE);
    }

    @Transactional
    public UrlEntity addUrl(String url, String name, String description) {
        if (urlRepository.existsByUrl(url)) {
            throw new IllegalArgumentException("Такой URL уже есть: " + url);
        }
        UrlEntity urlEntity = new UrlEntity(url, name, description);
        return urlRepository.save(urlEntity);
    }

    @Transactional
    public List<UrlEntity> addUrls(List<Map<String, String>> urls) {
        List<UrlEntity> savedUrls = new ArrayList<>();
        for (Map<String, String> urlData : urls) {
            try {
                UrlEntity saved = addUrl(
                        urlData.get("url"),
                        urlData.get("name"),
                        urlData.get("description")
                );
                savedUrls.add(saved);
            } catch (IllegalArgumentException e) {
                logger.warn("Такой URL уже есть: {}", urlData.get("url"));
            }
        }
        return savedUrls;
    }

    @Transactional
    public CompletableFuture<Map<String, Object>> processAllUrls() {
        List<UrlEntity> activeUrls = urlRepository.findByActiveTrueOrderByCreatedAtDesc();

        if (activeUrls.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of(
                    "message", "Нет ни одного URL",
                    "processedCount", 0
            ));
        }

        logger.info("начинаем обработку {} URLs", activeUrls.size());

        List<CompletableFuture<UrlResultEntity>> futures = new ArrayList<>();

        for (UrlEntity url : activeUrls) {
            CompletableFuture<UrlResultEntity> future = CompletableFuture.supplyAsync(() -> {
                return processSingleUrl(url);
            }, threadPoolExecutor);
            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    List<UrlResultEntity> results = futures.stream()
                            .map(CompletableFuture::join)
                            .filter(Objects::nonNull)
                            .toList();

                    logger.info("Процесс обработки {} URLs завершен", results.size());

                    return Map.of(
                            "message", "Обработка URL завершена",
                            "processedCount", results.size(),
                            "status", "COMPLETED"
                    );
                });
    }

    @Transactional
    public UrlResultEntity processSingleUrl(UrlEntity urlEntity) {
        long startTime = System.currentTimeMillis();
        logger.debug("Обработка URL: {}", urlEntity.getUrl());

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlEntity.getUrl()))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "URL-Parser-Bot/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long responseTime = System.currentTimeMillis() - startTime;

            UrlResultEntity result = new UrlResultEntity(urlEntity, response.statusCode(), responseTime);
            extractContactInfo(response.body(), result);

            UrlResultEntity savedResult = urlResultRepository.save(result);
            logger.info("Успешно обработан URL: {} - Status: {} - Time: {}ms",
                    urlEntity.getUrl(), response.statusCode(), responseTime);

            return savedResult;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            UrlResultEntity result = new UrlResultEntity(urlEntity, -1, responseTime);
            result.setErrorMessage(e.getMessage());

            UrlResultEntity savedResult = urlResultRepository.save(result);
            logger.error("Ошибка обработки URL: {} - Error: {}", urlEntity.getUrl(), e.getMessage());

            return savedResult;
        }
    }

    private void extractContactInfo(String content, UrlResultEntity result) {
        if (content == null) return;

        // Ищем контактную инфу через регулярку
        String emailRegex = "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b";
        String phoneRegex = "(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}";
        String addressRegex = "\\b(ул\\.|улица|проспект|пр\\.|бульвар|б-р|переулок|пер\\.)[^,.]{1,50},\\s*[^,.]{1,50}";
        String hoursRegex = "(пн|вт|ср|чт|пт|сб|вс|понед|вторник|среда|четверг|пятница|суббота|воскресенье)[^.]*\\d{1,2}[:.]\\d{2}[^.]*\\d{1,2}[:.]\\d{2}";

        Pattern emailPattern = Pattern.compile(emailRegex);
        Matcher emailMatcher = emailPattern.matcher(content);
        Set<String> emails = new HashSet<>();
        while (emailMatcher.find()) {
            emails.add(emailMatcher.group());
        }
        if (!emails.isEmpty()) {
            result.setEmail(String.join(", ", emails));
        }

        Pattern phonePattern = Pattern.compile(phoneRegex);
        Matcher phoneMatcher = phonePattern.matcher(content);
        Set<String> phones = new HashSet<>();
        while (phoneMatcher.find()) {
            phones.add(phoneMatcher.group());
        }
        if (!phones.isEmpty()) {
            result.setPhone(String.join(", ", phones));
        }

        Pattern addressPattern = Pattern.compile(addressRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher addressMatcher = addressPattern.matcher(content);
        if (addressMatcher.find()) {
            result.setAddress(addressMatcher.group());
        }

        Pattern hoursPattern = Pattern.compile(hoursRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher hoursMatcher = hoursPattern.matcher(content);
        if (hoursMatcher.find()) {
            result.setWorkingHours(hoursMatcher.group());
        }
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalUrls = urlRepository.count();
        long activeUrls = urlRepository.countActiveUrls();
        long totalResults = urlResultRepository.count();
        long successCount = urlResultRepository.countByStatusCode(200);
        Double avgResponseTime = urlResultRepository.findAverageResponseTime();

        stats.put("totalUrls", totalUrls);
        stats.put("activeUrls", activeUrls);
        stats.put("totalResults", totalResults);
        stats.put("successfulRequests", successCount);
        stats.put("successRate", totalResults > 0 ? String.format("%.1f%%", successCount * 100.0 / totalResults) : "0%");
        stats.put("averageResponseTime", avgResponseTime != null ? String.format("%.2f ms", avgResponseTime) : "N/A");
        stats.put("threadPoolActiveThreads", threadPoolExecutor.getActiveCount());
        stats.put("threadPoolQueueSize", threadPoolExecutor.getQueue().size());
        stats.put("threadPoolCompletedTasks", threadPoolExecutor.getCompletedTaskCount());

        return stats;
    }

    public ThreadPoolExecutor getThreadPoolExecutor() {
        return threadPoolExecutor;
    }
}