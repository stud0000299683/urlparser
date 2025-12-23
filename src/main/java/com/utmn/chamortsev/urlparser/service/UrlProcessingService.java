package com.utmn.chamortsev.urlparser.service;

import com.utmn.chamortsev.urlparser.entity.UrlEntity;
import com.utmn.chamortsev.urlparser.entity.UrlResultEntity;
import com.utmn.chamortsev.urlparser.repository.UrlRepository;
import com.utmn.chamortsev.urlparser.repository.UrlResultRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.net.http.*;
import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;


@Service
public class UrlProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(UrlProcessingService.class);

    private final UrlRepository urlRepository;
    private final UrlResultRepository urlResultRepository;
    private final ThreadPoolExecutor threadPoolExecutor;
    private final ForkJoinPool forkJoinPool;
    private final HttpClient httpClient;

    private static final int THREAD_POOL_SIZE = 5;
    private static final int FORK_JOIN_PARALLELISM = 8;
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

        this.forkJoinPool = new ForkJoinPool(FORK_JOIN_PARALLELISM);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        logger.info("ThreadPoolExecutor запущен с {} потоками", THREAD_POOL_SIZE);
        logger.info("ForkJoinPool запущен с параллелизмом {}", FORK_JOIN_PARALLELISM);
    }

    // 🔥 КЭШ МЕТОДЫ - ПОСЛЕ КОНСТРУКТОРА
    @Cacheable(value = "urlById", key = "#id")
    public UrlEntity getUrlById(Long id) {
        logger.info("🔴 CACHE MISS - DB query for URL ID: {}", id);
        return urlRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("URL not found: " + id));
    }

    @Cacheable(value = "urls")
    public List<UrlEntity> getAllUrls() {
        logger.info("🔴 CACHE MISS - DB query for ALL URLs");
        return urlRepository.findAll();
    }

    @Caching(evict = {
            @CacheEvict(value = "urlById", key = "#url.id"),
            @CacheEvict(value = "urls", allEntries = true)
    })
    @Transactional
    public UrlEntity updateUrlEntity(UrlEntity url) {
        logger.info("🟡 CACHE EVICT - updating URL ID: {}", url.getId());
        return urlRepository.save(url);
    }
    @CacheEvict(value = {"urlById", "urls"}, key = "#id")
    @Transactional
    public void deleteUrlById(Long id) {
        logger.info("🟡 CACHE EVICT - deleting URL ID: {}", id);
        if (urlRepository.existsById(id)) {
            urlRepository.deleteById(id);
        }
    }

    // НОВЫЙ МЕТОД: ForkJoin обработка
    @Transactional
    public CompletableFuture<Map<String, Object>> processUrlsWithForkJoin() {
        List<UrlEntity> activeUrls = urlRepository.findByActiveTrueOrderByCreatedAtDesc();

        if (activeUrls.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of(
                    "message", "Нет ни одного URL для обработки",
                    "processedCount", 0,
                    "aggregatedStats", Map.of()
            ));
        }

        logger.info("Запуск ForkJoin обработки для {} URLs", activeUrls.size());

        return CompletableFuture.supplyAsync(() -> {
            UrlProcessingTask mainTask = new UrlProcessingTask(activeUrls, this);
            Map<String, Object> forkJoinResult = forkJoinPool.invoke(mainTask);

            // Добавляем агрегированную статистику
            Map<String, Object> finalResult = enhanceWithAggregatedStats(forkJoinResult);
            logger.info("ForkJoin обработка завершена. Обработано {} URLs",
                    forkJoinResult.get("processedCount"));

            return finalResult;
        }, threadPoolExecutor);
    }
    // Метод для использования в ForkJoin задачах
    public Map<String, Object> processSingleUrlForForkJoin(UrlEntity urlEntity) {
        long startTime = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlEntity.getUrl()))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "URL-Parser-Bot/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long responseTime = System.currentTimeMillis() - startTime;

            Map<String, Object> result = new HashMap<>();
            result.put("urlId", urlEntity.getId());
            result.put("url", urlEntity.getUrl());
            result.put("name", urlEntity.getName());
            result.put("statusCode", response.statusCode());
            result.put("responseTime", responseTime);
            result.put("success", response.statusCode() == 200);
            result.put("processedAt", new Date());

            // Извлекаем контактную информацию
            Map<String, String> contactInfo = extractContactInfo(response.body());
            result.putAll(contactInfo);

            // Подсчет количества найденных элементов
            result.put("emailCount", countEmails(contactInfo.get("email")));
            result.put("phoneCount", countPhones(contactInfo.get("phone")));
            result.put("totalContactsFound", calculateTotalContacts(contactInfo));

            // Сохраняем в базу
            saveUrlResult(urlEntity, response.statusCode(), responseTime, contactInfo, null);

            return result;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Map<String, Object> errorResult = createErrorResult(urlEntity, e.getMessage());
            saveUrlResult(urlEntity, -1, responseTime, Collections.emptyMap(), e.getMessage());
            return errorResult;
        }
    }

    // Агрегированная статистика
    private Map<String, Object> enhanceWithAggregatedStats(Map<String, Object> forkJoinResult) {
        Map<String, Object> enhanced = new HashMap<>(forkJoinResult);

        int processedCount = (int) forkJoinResult.getOrDefault("processedCount", 0);
        int successCount = (int) forkJoinResult.getOrDefault("successCount", 0);
        long totalResponseTime = (long) forkJoinResult.getOrDefault("totalResponseTime", 0L);

        // Основная агрегированная статистика
        Map<String, Object> aggregatedStats = new HashMap<>();
        aggregatedStats.put("totalUrlsProcessed", processedCount);
        aggregatedStats.put("successfulUrls", successCount);
        aggregatedStats.put("successRate", processedCount > 0 ?
                String.format("%.1f%%", successCount * 100.0 / processedCount) : "0%");
        aggregatedStats.put("averageResponseTime", successCount > 0 ?
                String.format("%.2f ms", totalResponseTime * 1.0 / successCount) : "N/A");

        // Статистика по контактам
        aggregatedStats.put("totalEmailsFound", forkJoinResult.getOrDefault("emailsFound", 0));
        aggregatedStats.put("totalPhonesFound", forkJoinResult.getOrDefault("phonesFound", 0));
        aggregatedStats.put("totalAddressesFound", forkJoinResult.getOrDefault("addressesFound", 0));

        // Эффективность извлечения
        aggregatedStats.put("emailExtractionRate", processedCount > 0 ?
                String.format("%.1f%%", (int)forkJoinResult.getOrDefault("emailsFound", 0) * 100.0 / processedCount) : "0%");
        aggregatedStats.put("phoneExtractionRate", processedCount > 0 ?
                String.format("%.1f%%", (int)forkJoinResult.getOrDefault("phonesFound", 0) * 100.0 / processedCount) : "0%");

        // Производительность
        aggregatedStats.put("totalBatchesProcessed", forkJoinResult.getOrDefault("totalBatches", 0));
        aggregatedStats.put("forkJoinParallelism", FORK_JOIN_PARALLELISM);

        enhanced.put("aggregatedStats", aggregatedStats);
        enhanced.put("processingType", "FORK_JOIN_RECURSIVE");
        enhanced.put("timestamp", new Date());

        return enhanced;
    }

    // Подсчет количества элементов
    private int countEmails(String email) {
        if (email == null || email.isEmpty()) return 0;
        return email.split(",").length;
    }

    private int countPhones(String phone) {
        if (phone == null || phone.isEmpty()) return 0;
        return phone.split(",").length;
    }

    private int calculateTotalContacts(Map<String, String> contacts) {
        int total = 0;
        if (contacts.containsKey("email") && !contacts.get("email").isEmpty()) total++;
        if (contacts.containsKey("phone") && !contacts.get("phone").isEmpty()) total++;
        if (contacts.containsKey("address") && !contacts.get("address").isEmpty()) total++;
        if (contacts.containsKey("workingHours") && !contacts.get("workingHours").isEmpty()) total++;
        return total;
    }

    // СИНХРОННЫЙ МЕТОД - для оригинального контроллера
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

    // АСИНХРОННЫЙ МЕТОД - для Async контроллера
    @Transactional
    public CompletableFuture<List<Map<String, Object>>> processAllUrlsAsync() {
        List<UrlEntity> activeUrls = urlRepository.findByActiveTrueOrderByCreatedAtDesc();

        if (activeUrls.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        logger.info("Начинаем асинхронную обработку {} URLs", activeUrls.size());

        // Создаем список CompletableFuture для каждого URL
        List<CompletableFuture<Map<String, Object>>> urlFutures = activeUrls.stream()
                .map(this::processUrlWithTransformations)
                .collect(Collectors.toList());

        // Объединяем все futures в один
        return CompletableFuture.allOf(urlFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> urlFutures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()))
                .thenApply(results -> {
                    logger.info("Асинхронная обработка завершена, обработано {} URLs", results.size());
                    return results;
                });
    }

    // Асинхронная обработка одного URL с преобразованиями
    private CompletableFuture<Map<String, Object>> processUrlWithTransformations(UrlEntity urlEntity) {
        return CompletableFuture.supplyAsync(() -> {
                    try {
                        // Получаем базовые данные URL
                        Map<String, Object> baseData = getUrlBaseData(urlEntity);
                        return baseData;
                    } catch (Exception e) {
                        logger.error("Ошибка получения базовых данных для URL: {}", urlEntity.getUrl(), e);
                        return createErrorResult(urlEntity, e.getMessage());
                    }
                }, threadPoolExecutor)
                .thenApply(this::applyDataTransformations) // Применяем преобразования
                .thenCombine(getAdditionalUrlInfo(urlEntity.getId()), this::combineResults) // Объединяем с дополнительной информацией
                .exceptionally(ex -> {
                    logger.error("Ошибка в цепочке обработки для URL: {}", urlEntity.getUrl(), ex);
                    return createErrorResult(urlEntity, ex.getMessage());
                });
    }

    // Получение базовых данных URL
    private Map<String, Object> getUrlBaseData(UrlEntity urlEntity) {
        long startTime = System.currentTimeMillis();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlEntity.getUrl()))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "URL-Parser-Bot/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long responseTime = System.currentTimeMillis() - startTime;

            // Создаем результат
            Map<String, Object> result = new HashMap<>();
            result.put("urlId", urlEntity.getId());
            result.put("url", urlEntity.getUrl());
            result.put("name", urlEntity.getName());
            result.put("statusCode", response.statusCode());
            result.put("responseTime", responseTime);
            result.put("success", response.statusCode() == 200);
            result.put("processedAt", new Date());

            // Извлекаем контактную информацию
            Map<String, String> contactInfo = extractContactInfo(response.body());
            result.putAll(contactInfo);

            // Сохраняем в базу
            saveUrlResult(urlEntity, response.statusCode(), responseTime, contactInfo, null);

            logger.debug("Успешно обработан URL: {} - Status: {} - Time: {}ms",
                    urlEntity.getUrl(), response.statusCode(), responseTime);

            return result;

        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            Map<String, Object> errorResult = createErrorResult(urlEntity, e.getMessage());
            saveUrlResult(urlEntity, -1, responseTime, Collections.emptyMap(), e.getMessage());
            return errorResult;
        }
    }

    // Применение преобразований к данным
    private Map<String, Object> applyDataTransformations(Map<String, Object> data) {
        Map<String, Object> transformed = new HashMap<>(data);

        // Фильтрация: помечаем медленные запросы
        Long responseTime = (Long) data.get("responseTime");
        if (responseTime != null && responseTime > 5000) {
            transformed.put("performance", "SLOW");
        } else if (responseTime != null && responseTime > 2000) {
            transformed.put("performance", "MEDIUM");
        } else {
            transformed.put("performance", "FAST");
        }

        // Форматирование: улучшаем читаемость данных
        String email = (String) data.get("email");
        if (email != null && !email.isEmpty()) {
            transformed.put("emailFormatted", formatEmail(email));
        }

        String phone = (String) data.get("phone");
        if (phone != null && !phone.isEmpty()) {
            transformed.put("phoneFormatted", formatPhone(phone));
        }

        // Обогащение: добавляем оценку качества данных
        int dataQualityScore = calculateDataQualityScore(data);
        transformed.put("dataQualityScore", dataQualityScore);
        transformed.put("dataQuality", dataQualityScore >= 8 ? "HIGH" :
                dataQualityScore >= 5 ? "MEDIUM" : "LOW");

        return transformed;
    }

    // Асинхронное получение дополнительной информации (например, из кэша или другого сервиса)
    private CompletableFuture<Map<String, Object>> getAdditionalUrlInfo(Long urlId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Map<String, Object> additionalInfo = new HashMap<>();

                // Симулируем получение дополнительных данных (например, из кэша, внешнего API и т.д.)
                Thread.sleep(100); // Имитация задержки

                // Получаем исторические данные
                List<UrlResultEntity> history = urlResultRepository.findByUrlEntityIdOrderByProcessedAtDesc(urlId);
                if (!history.isEmpty()) {
                    additionalInfo.put("previousSuccessRate", calculateSuccessRate(history));
                    additionalInfo.put("totalProcessings", history.size());

                    // Среднее время ответа из истории
                    Double avgHistoricalTime = history.stream()
                            .filter(h -> h.getStatusCode() == 200)
                            .mapToLong(UrlResultEntity::getResponseTime)
                            .average()
                            .orElse(0.0);
                    additionalInfo.put("avgHistoricalResponseTime", avgHistoricalTime);
                }

                // Рейтинг надежности (симулируем)
                additionalInfo.put("reliabilityRating", calculateReliabilityRating(urlId));

                return additionalInfo;

            } catch (Exception e) {
                logger.warn("Ошибка получения дополнительной информации для URL ID: {}", urlId, e);
                return Collections.emptyMap();
            }
        }, threadPoolExecutor);
    }

    // Объединение основных и дополнительных данных
    private Map<String, Object> combineResults(Map<String, Object> mainData, Map<String, Object> additionalInfo) {
        Map<String, Object> combined = new HashMap<>(mainData);
        combined.putAll(additionalInfo);

        // Создаем сводную оценку
        int dataQualityScore = (Integer) mainData.getOrDefault("dataQualityScore", 0);
        double reliabilityRating = (Double) additionalInfo.getOrDefault("reliabilityRating", 0.5);
        double performanceScore = calculatePerformanceScore(mainData);

        double overallScore = (dataQualityScore / 10.0 * 0.4) +
                (reliabilityRating * 0.4) +
                (performanceScore * 0.2);

        combined.put("overallScore", Math.round(overallScore * 100.0) / 100.0);
        combined.put("overallRating", overallScore >= 0.8 ? "EXCELLENT" :
                overallScore >= 0.6 ? "GOOD" :
                        overallScore >= 0.4 ? "FAIR" : "POOR");

        return combined;
    }

    // ОРИГИНАЛЬНЫЙ МЕТОД обработки одного URL
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
            extractContactInfoToEntity(response.body(), result);

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

    // ОРИГИНАЛЬНЫЙ МЕТОД извлечения контактов в Entity
    private void extractContactInfoToEntity(String content, UrlResultEntity result) {
        if (content == null) return;

        Map<String, String> contacts = extractContactInfo(content);

        if (contacts.containsKey("email")) {
            result.setEmail(contacts.get("email"));
        }
        if (contacts.containsKey("phone")) {
            result.setPhone(contacts.get("phone"));
        }
        if (contacts.containsKey("address")) {
            result.setAddress(contacts.get("address"));
        }
        if (contacts.containsKey("workingHours")) {
            result.setWorkingHours(contacts.get("workingHours"));
        }
    }

    // Вспомогательные методы
    private Map<String, String> extractContactInfo(String content) {
        Map<String, String> contacts = new HashMap<>();
        if (content == null) return contacts;

        // Регулярные выражения для извлечения контактов
        String emailRegex = "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b";
        String phoneRegex = "(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}";
        String addressRegex = "\\b(ул\\.|улица|проспект|пр\\.|бульвар|б-р|переулок|пер\\.)[^,.]{1,50},\\s*[^,.]{1,50}";
        String hoursRegex = "(пн|вт|ср|чт|пт|сб|вс|понед|вторник|среда|четверг|пятница|суббота|воскресенье)[^.]*\\d{1,2}[:.]\\d{2}[^.]*\\d{1,2}[:.]\\d{2}";

        // Извлечение email
        Pattern emailPattern = Pattern.compile(emailRegex);
        Matcher emailMatcher = emailPattern.matcher(content);
        Set<String> emails = new HashSet<>();
        while (emailMatcher.find()) {
            emails.add(emailMatcher.group());
        }
        if (!emails.isEmpty()) {
            contacts.put("email", String.join(", ", emails));
        }

        // Извлечение телефонов
        Pattern phonePattern = Pattern.compile(phoneRegex);
        Matcher phoneMatcher = phonePattern.matcher(content);
        Set<String> phones = new HashSet<>();
        while (phoneMatcher.find()) {
            phones.add(phoneMatcher.group());
        }
        if (!phones.isEmpty()) {
            contacts.put("phone", String.join(", ", phones));
        }

        // Извлечение адреса
        Pattern addressPattern = Pattern.compile(addressRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher addressMatcher = addressPattern.matcher(content);
        if (addressMatcher.find()) {
            contacts.put("address", addressMatcher.group());
        }

        // Извлечение часов работы
        Pattern hoursPattern = Pattern.compile(hoursRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher hoursMatcher = hoursPattern.matcher(content);
        if (hoursMatcher.find()) {
            contacts.put("workingHours", hoursMatcher.group());
        }

        return contacts;
    }

    private String formatEmail(String email) {
        return email.toLowerCase().trim();
    }

    private String formatPhone(String phone) {
        return phone.replaceAll("\\s+", " ").trim();
    }

    private int calculateDataQualityScore(Map<String, Object> data) {
        int score = 0;
        if (data.get("email") != null && !((String) data.get("email")).isEmpty()) score += 3;
        if (data.get("phone") != null && !((String) data.get("phone")).isEmpty()) score += 3;
        if (data.get("address") != null && !((String) data.get("address")).isEmpty()) score += 2;
        if (data.get("workingHours") != null && !((String) data.get("workingHours")).isEmpty()) score += 2;
        return score;
    }

    private double calculateSuccessRate(List<UrlResultEntity> history) {
        long successCount = history.stream()
                .filter(h -> h.getStatusCode() == 200)
                .count();
        return history.isEmpty() ? 0.0 : (double) successCount / history.size();
    }

    private double calculateReliabilityRating(Long urlId) {
        // Симулируем расчет рейтинга надежности
        return ThreadLocalRandom.current().nextDouble(0.3, 1.0);
    }

    private double calculatePerformanceScore(Map<String, Object> data) {
        Long responseTime = (Long) data.get("responseTime");
        if (responseTime == null) return 0.0;

        if (responseTime < 1000) return 1.0;
        if (responseTime < 3000) return 0.7;
        if (responseTime < 5000) return 0.4;
        return 0.1;
    }

    private Map<String, Object> createErrorResult(UrlEntity urlEntity, String errorMessage) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("urlId", urlEntity.getId());
        errorResult.put("url", urlEntity.getUrl());
        errorResult.put("name", urlEntity.getName());
        errorResult.put("success", false);
        errorResult.put("error", errorMessage);
        errorResult.put("processedAt", new Date());
        return errorResult;
    }

    private void saveUrlResult(UrlEntity urlEntity, Integer statusCode, Long responseTime,
                               Map<String, String> contactInfo, String errorMessage) {
        try {
            UrlResultEntity result = new UrlResultEntity(urlEntity, statusCode, responseTime);
            if (contactInfo != null) {
                result.setEmail(contactInfo.get("email"));
                result.setPhone(contactInfo.get("phone"));
                result.setAddress(contactInfo.get("address"));
                result.setWorkingHours(contactInfo.get("workingHours"));
            }
            if (errorMessage != null) {
                result.setErrorMessage(errorMessage);
            }
            urlResultRepository.save(result);
        } catch (Exception e) {
            logger.error("Ошибка сохранения результата для URL: {}", urlEntity.getUrl(), e);
        }
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

    public ForkJoinPool getForkJoinPool() {
        return forkJoinPool;
    }

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void notifyResult(Long urlId, Map<String, Object> result) {
        messagingTemplate.convertAndSend("/topic/url/" + urlId, result);
    }


}

