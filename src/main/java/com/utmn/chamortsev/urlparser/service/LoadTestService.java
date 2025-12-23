package com.utmn.chamortsev.urlparser.service;

import com.utmn.chamortsev.urlparser.dto.LoadTestRequest;
import com.utmn.chamortsev.urlparser.entity.UrlEntity;
import com.utmn.chamortsev.urlparser.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class LoadTestService {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestService.class);

    private final UrlRepository urlRepository;
    private final UrlProcessingService urlProcessingService;
    private final ThreadPoolExecutor loadTestExecutor;

    // Список тестовых сайтов для генерации URL
    private static final String[] TEST_DOMAINS = {
            "https://httpbin.org",
            "https://jsonplaceholder.typicode.com",
            "https://reqres.in",
            "https://api.github.com",
            "https://api.openweathermap.org",
            "https://api.coingecko.com"
    };

    private static final String[] TEST_PATHS = {
            "/html",
            "/json",
            "/xml",
            "/delay/1",
            "/delay/2",
            "/delay/3",
            "/status/200",
            "/status/404",
            "/status/500",
            "/headers",
            "/ip",
            "/user-agent"
    };

    private static final String[] COMPANY_NAMES = {
            "TechCorp", "InnovateLtd", "GlobalSolutions", "DigitalMinds",
            "FutureTech", "SmartSystems", "DataHub", "CloudNetworks",
            "WebCrafters", "CodeMasters", "AppBuilders", "CyberSecure"
    };

    private final AtomicInteger activeLoadTests = new AtomicInteger(0);
    private final Map<String, LoadTestStats> activeTests = new ConcurrentHashMap<>();

    public LoadTestService(UrlRepository urlRepository, UrlProcessingService urlProcessingService) {
        this.urlRepository = urlRepository;
        this.urlProcessingService = urlProcessingService;

        // Создаем отдельный пул потоков для нагрузочного тестирования
        this.loadTestExecutor = new ThreadPoolExecutor(
                5,  // core pool size
                50, // max pool size
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        logger.info("LoadTestService инициализирован");
    }

    /**
     * Генерация тестовых URL
     */
    @Transactional
    public List<UrlEntity> generateTestUrls(int count) {
        logger.info("Генерация {} тестовых URL", count);

        List<UrlEntity> generatedUrls = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            try {
                String url = generateRandomUrl();
                String name = generateRandomCompanyName();
                String description = generateRandomDescription();

                // Проверяем, существует ли уже такой URL
                if (!urlRepository.existsByUrl(url)) {
                    UrlEntity urlEntity = new UrlEntity(url, name, description);
                    urlEntity.setActive(true);

                    UrlEntity saved = urlRepository.save(urlEntity);
                    generatedUrls.add(saved);

                    if (generatedUrls.size() % 10 == 0) {
                        logger.debug("Сгенерировано {} URL из {}", generatedUrls.size(), count);
                    }
                }
            } catch (Exception e) {
                logger.warn("Ошибка при генерации URL: {}", e.getMessage());
            }
        }

        logger.info("Успешно сгенерировано {} тестовых URL", generatedUrls.size());
        return generatedUrls;
    }

    /**
     * Запуск нагрузочного тестирования
     */
    public Map<String, Object> startLoadTest(LoadTestRequest request) {
        String testId = UUID.randomUUID().toString().substring(0, 8);

        logger.info("Запуск нагрузочного теста ID: {} с параметрами: {} URL, {} потоков, {} секунд",
                testId, request.getUrlCount(), request.getThreadCount(), request.getDurationSeconds());

        // Создаем статистику для этого теста
        LoadTestStats stats = new LoadTestStats(testId, request);
        activeTests.put(testId, stats);
        activeLoadTests.incrementAndGet();

        // Запускаем тест в отдельном потоке
        loadTestExecutor.submit(() -> {
            try {
                executeLoadTest(testId, request, stats);
            } catch (Exception e) {
                logger.error("Ошибка во время нагрузочного теста ID: {}", testId, e);
                stats.setStatus("FAILED");
                stats.setErrorMessage(e.getMessage());
            } finally {
                activeTests.remove(testId);
                activeLoadTests.decrementAndGet();
            }
        });

        return Map.of(
                "testId", testId,
                "status", "STARTED",
                "message", "Нагрузочный тест запущен",
                "startTime", new Date(),
                "activeTests", activeLoadTests.get()
        );
    }

    /**
     * Выполнение нагрузочного теста
     */
    private void executeLoadTest(String testId, LoadTestRequest request, LoadTestStats stats) {
        stats.setStatus("RUNNING");

        // Шаг 1: Генерация тестовых URL если нужно
        List<UrlEntity> testUrls;
        if (Boolean.TRUE.equals(request.getGenerateUrls())) {
            testUrls = generateTestUrls(request.getUrlCount());
            stats.setGeneratedUrls(testUrls.size());
        } else {
            testUrls = urlRepository.findByActiveTrueOrderByCreatedAtDesc();
            stats.setGeneratedUrls(testUrls.size());
        }

        if (testUrls.isEmpty()) {
            stats.setStatus("FAILED");
            stats.setErrorMessage("Нет URL для тестирования");
            return;
        }

        // Шаг 2: Запуск параллельной обработки
        ExecutorService testExecutor = Executors.newFixedThreadPool(request.getThreadCount());
        CountDownLatch latch = new CountDownLatch(request.getThreadCount());

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (request.getDurationSeconds() * 1000L);

        for (int i = 0; i < request.getThreadCount(); i++) {
            int threadNum = i;
            testExecutor.submit(() -> {
                Thread.currentThread().setName("LoadTest-" + testId + "-Thread-" + threadNum);

                try {
                    Random random = new Random();

                    while (System.currentTimeMillis() < endTime && !Thread.currentThread().isInterrupted()) {
                        // Выбираем случайный URL из списка
                        UrlEntity url = testUrls.get(random.nextInt(testUrls.size()));

                        long requestStart = System.currentTimeMillis();

                        try {
                            // Выполняем запрос в зависимости от типа теста
                            switch (request.getTestType().toUpperCase()) {
                                case "ASYNC":
                                    urlProcessingService.processSingleUrlForForkJoin(url);
                                    break;
                                case "FORKJOIN":
                                    // Используем ForkJoin обработку
                                    urlProcessingService.processSingleUrlForForkJoin(url);
                                    break;
                                case "SYNC":
                                    urlProcessingService.processSingleUrl(url);
                                    break;
                                default:
                                    urlProcessingService.processSingleUrlForForkJoin(url);
                            }

                            long responseTime = System.currentTimeMillis() - requestStart;
                            stats.recordSuccess(responseTime);

                        } catch (Exception e) {
                            stats.recordError(e.getMessage());
                            logger.debug("Ошибка при обработке URL {}: {}", url.getUrl(), e.getMessage());
                        }

                        // Ждем указанный интервал
                        try {
                            Thread.sleep(request.getRequestIntervalMs());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Ждем завершения всех потоков
        try {
            latch.await(request.getDurationSeconds() + 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        testExecutor.shutdown();

        // Завершаем тест
        stats.setStatus("COMPLETED");
        stats.setEndTime(new Date());

        logger.info("Нагрузочный тест ID: {} завершен. Успешных запросов: {}, Ошибок: {}, Среднее время: {} мс",
                testId, stats.getSuccessCount(), stats.getErrorCount(), stats.getAverageResponseTime());
    }

    /**
     * Получение статуса нагрузочного теста
     */
    public Map<String, Object> getLoadTestStatus(String testId) {
        LoadTestStats stats = activeTests.get(testId);

        if (stats == null) {
            return Map.of(
                    "testId", testId,
                    "status", "NOT_FOUND",
                    "message", "Тест не найден или завершен"
            );
        }

        return stats.toMap();
    }

    /**
     * Получение списка активных тестов
     */
    public Map<String, Object> getActiveLoadTests() {
        List<Map<String, Object>> tests = activeTests.values().stream()
                .map(LoadTestStats::toMap)
                .collect(Collectors.toList());

        return Map.of(
                "activeTests", activeLoadTests.get(),
                "tests", tests,
                "timestamp", new Date()
        );
    }

    /**
     * Остановка всех нагрузочных тестов
     */
    public Map<String, Object> stopAllLoadTests() {
        int stoppedCount = activeTests.size();

        loadTestExecutor.shutdownNow();
        activeTests.clear();
        activeLoadTests.set(0);

        try {
            loadTestExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Остановлено {} нагрузочных тестов", stoppedCount);

        return Map.of(
                "stoppedCount", stoppedCount,
                "message", "Все нагрузочные тесты остановлены",
                "timestamp", new Date()
        );
    }

    // Вспомогательные методы для генерации данных

    private String generateRandomUrl() {
        Random random = new Random();
        String domain = TEST_DOMAINS[random.nextInt(TEST_DOMAINS.length)];
        String path = TEST_PATHS[random.nextInt(TEST_PATHS.length)];

        // Иногда добавляем параметры
        if (random.nextBoolean()) {
            String[] params = {"?test=true", "?debug=1", "?format=json", "?delay=1"};
            path += params[random.nextInt(params.length)];
        }

        return domain + path;
    }

    private String generateRandomCompanyName() {
        Random random = new Random();
        String name = COMPANY_NAMES[random.nextInt(COMPANY_NAMES.length)];

        // Добавляем суффикс
        String[] suffixes = {" Inc.", " LLC", " GmbH", " Ltd.", " Corp.", " Solutions"};
        if (random.nextBoolean()) {
            name += suffixes[random.nextInt(suffixes.length)];
        }

        return name + " #" + (random.nextInt(999) + 1);
    }

    private String generateRandomDescription() {
        String[] descriptions = {
                "Тестовый сайт для нагрузочного тестирования",
                "Демонстрационный ресурс для парсинга контактов",
                "Веб-сайт компании с контактной информацией",
                "Онлайн-платформа для проверки работы парсера",
                "Технический ресурс с различными типами контента",
                "Сайт с примерами HTML страниц для тестирования"
        };

        Random random = new Random();
        return descriptions[random.nextInt(descriptions.length)];
    }

    /**
     * Внутренний класс для хранения статистики нагрузочного теста
     */
    private static class LoadTestStats {
        private final String testId;
        private final LoadTestRequest request;
        private String status;
        private String errorMessage;
        private Date startTime;
        private Date endTime;
        private int generatedUrls;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger errorCount = new AtomicInteger(0);
        private final LongAdder totalResponseTime = new LongAdder();
        private final List<String> errors = new CopyOnWriteArrayList<>();

        public LoadTestStats(String testId, LoadTestRequest request) {
            this.testId = testId;
            this.request = request;
            this.status = "CREATED";
            this.startTime = new Date();
        }

        public void recordSuccess(long responseTime) {
            successCount.incrementAndGet();
            totalResponseTime.add(responseTime);
        }

        public void recordError(String error) {
            errorCount.incrementAndGet();
            if (errors.size() < 100) { // Ограничиваем количество сохраняемых ошибок
                errors.add(error);
            }
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("testId", testId);
            map.put("status", status);
            map.put("startTime", startTime);
            map.put("endTime", endTime);
            map.put("generatedUrls", generatedUrls);
            map.put("successCount", successCount.get());
            map.put("errorCount", errorCount.get());
            map.put("totalRequests", successCount.get() + errorCount.get());
            map.put("averageResponseTime", successCount.get() > 0 ?
                    String.format("%.2f ms", totalResponseTime.sum() / (double) successCount.get()) : "N/A");
            map.put("requestsPerSecond", calculateRPS());
            map.put("errorRate", String.format("%.2f%%",
                    (errorCount.get() * 100.0) / Math.max(1, successCount.get() + errorCount.get())));

            if (errorMessage != null) {
                map.put("errorMessage", errorMessage);
            }

            if (!errors.isEmpty()) {
                map.put("recentErrors", errors.subList(0, Math.min(5, errors.size())));
            }

            // Параметры теста
            map.put("testType", request.getTestType());
            map.put("threadCount", request.getThreadCount());
            map.put("durationSeconds", request.getDurationSeconds());
            map.put("requestIntervalMs", request.getRequestIntervalMs());

            return map;
        }

        private String calculateRPS() {
            if (endTime == null || startTime == null) {
                return "N/A";
            }
            long durationMs = endTime.getTime() - startTime.getTime();
            if (durationMs == 0) {
                return "N/A";
            }
            double totalRequests = successCount.get() + errorCount.get();
            double rps = totalRequests / (durationMs / 1000.0);
            return String.format("%.2f", rps);
        }

        // Геттеры и сеттеры
        public String getTestId() { return testId; }
        public LoadTestRequest getRequest() { return request; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Date getStartTime() { return startTime; }
        public void setStartTime(Date startTime) { this.startTime = startTime; }
        public Date getEndTime() { return endTime; }
        public void setEndTime(Date endTime) { this.endTime = endTime; }
        public int getGeneratedUrls() { return generatedUrls; }
        public void setGeneratedUrls(int generatedUrls) { this.generatedUrls = generatedUrls; }
        public int getSuccessCount() { return successCount.get(); }
        public int getErrorCount() { return errorCount.get(); }
        public double getAverageResponseTime() {
            return successCount.get() > 0 ? totalResponseTime.sum() / (double) successCount.get() : 0;
        }
    }
}