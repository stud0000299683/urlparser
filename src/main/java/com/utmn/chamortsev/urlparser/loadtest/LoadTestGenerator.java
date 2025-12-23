package com.utmn.chamortsev.urlparser.loadtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoadTestGenerator implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LoadTestGenerator.class);
    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicInteger requestCounter = new AtomicInteger(0);
    private final AtomicInteger errorCounter = new AtomicInteger(0);

    private static final String[] TEST_URLS = {
            "https://httpbin.org/html",
            "https://httpbin.org/xml",
            "https://httpbin.org/status/200",
            "https://httpbin.org/status/404",
            "https://httpbin.org/delay/1",
            "https://httpbin.org/delay/2"
    };

    @Override
    public void run(String... args) throws Exception {
        // Запускаем нагрузочное тестирование если указан флаг
        if (args.length > 0 && args[0].equals("--load-test")) {
            logger.info("Starting load test...");
            startLoadTest();
        }
    }

    public void startLoadTest() {
        ExecutorService executor = Executors.newFixedThreadPool(20);

        // Генерация тестовых данных
        generateTestUrls();

        // Запуск параллельных запросов
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    // Случайный выбор эндпоинта
                    String[] endpoints = {
                            "/api/async/process",
                            "/api/async/process/forkjoin",
                            "/api/urls/process"
                    };

                    String endpoint = endpoints[(int) (Math.random() * endpoints.length)];

                    Map<String, Object> response = restTemplate.postForObject(
                            "http://localhost:8080" + endpoint,
                            null,
                            Map.class
                    );

                    int count = requestCounter.incrementAndGet();
                    if (count % 10 == 0) {
                        logger.info("Completed {} requests", count);
                    }

                } catch (Exception e) {
                    errorCounter.incrementAndGet();
                    logger.error("Request failed", e);
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
            logger.info("Load test completed. Total requests: {}, Errors: {}",
                    requestCounter.get(), errorCounter.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void generateTestUrls() {
        logger.info("Generating test URLs...");

        List<Map<String, String>> testData = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Map<String, String> urlData = new HashMap<>();
            urlData.put("url", TEST_URLS[i % TEST_URLS.length]);
            urlData.put("name", "Test Site " + i);
            urlData.put("description", "Load test URL " + i);
            testData.add(urlData);
        }

        try {
            restTemplate.postForObject(
                    "http://localhost:8080/api/urls/batch",
                    testData,
                    Map.class
            );
            logger.info("Generated {} test URLs", testData.size());
        } catch (Exception e) {
            logger.error("Failed to generate test URLs", e);
        }
    }

    @Scheduled(fixedDelay = 60000) // Каждую минуту
    public void scheduledLoadTest() {
        logger.info("Running scheduled load test...");
        startLoadTest();
    }
}