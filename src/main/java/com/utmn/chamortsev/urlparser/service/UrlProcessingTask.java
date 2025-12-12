package com.utmn.chamortsev.urlparser.service;

import com.utmn.chamortsev.urlparser.entity.UrlEntity;

import java.util.*;
import java.util.concurrent.RecursiveTask;

// ForkJoin задачи
class UrlProcessingTask extends RecursiveTask<Map<String, Object>> {
    private static final int BATCH_SIZE = 3;
    private final List<UrlEntity> urls;
    private final UrlProcessingService service;
    private final int start;
    private final int end;

    public UrlProcessingTask(List<UrlEntity> urls, UrlProcessingService service) {
        this(urls, service, 0, urls.size());
    }

    private UrlProcessingTask(List<UrlEntity> urls, UrlProcessingService service, int start, int end) {
        this.urls = urls;
        this.service = service;
        this.start = start;
        this.end = end;
    }

    @Override
    protected Map<String, Object> compute() {
        int length = end - start;

        // Если задача маленькая - обрабатываем напрямую
        if (length <= BATCH_SIZE) {
            return processBatch();
        }

        // Разбиваем задачу на подзадачи
        int middle = start + length / 2;
        UrlProcessingTask leftTask = new UrlProcessingTask(urls, service, start, middle);
        UrlProcessingTask rightTask = new UrlProcessingTask(urls, service, middle, end);

        // Асинхронно запускаем подзадачи
        leftTask.fork();
        Map<String, Object> rightResult = rightTask.compute();
        Map<String, Object> leftResult = leftTask.join();

        // Объединяем результат
        return mergeResults(leftResult, rightResult);
    }

    private Map<String, Object> processBatch() {
        List<Map<String, Object>> batchResults = new ArrayList<>();
        Map<String, Object> batchStats = new HashMap<>();

        batchStats.put("batchSize", end - start);
        batchStats.put("batchStart", start);
        batchStats.put("batchEnd", end);

        for (int i = start; i < end; i++) {
            try {
                UrlEntity url = urls.get(i);
                Map<String, Object> result = service.processSingleUrlForForkJoin(url);
                batchResults.add(result);

                // Агрегируем статистику
                aggregateStats(batchStats, result);
            } catch (Exception e) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("url", urls.get(i).getUrl());
                errorResult.put("success", false);
                errorResult.put("error", e.getMessage());
                batchResults.add(errorResult);
            }
        }

        batchStats.put("processedCount", batchResults.size());
        batchStats.put("results", batchResults);

        return batchStats;
    }

    private void aggregateStats(Map<String, Object> stats, Map<String, Object> result) {
        // Счетчик успешных запросов
        if (Boolean.TRUE.equals(result.get("success"))) {
            int successCount = (int) stats.getOrDefault("successCount", 0);
            stats.put("successCount", successCount + 1);
        }

        // Сумма времени ответа
        Long responseTime = (Long) result.get("responseTime");
        if (responseTime != null) {
            long totalResponseTime = (long) stats.getOrDefault("totalResponseTime", 0L);
            stats.put("totalResponseTime", totalResponseTime + responseTime);
        }

        // Подсчет контактных данных
        if (result.get("email") != null && !((String) result.get("email")).isEmpty()) {
            int emailCount = (int) stats.getOrDefault("emailsFound", 0);
            stats.put("emailsFound", emailCount + 1);
        }
        if (result.get("phone") != null && !((String) result.get("phone")).isEmpty()) {
            int phoneCount = (int) stats.getOrDefault("phonesFound", 0);
            stats.put("phonesFound", phoneCount + 1);
        }
        if (result.get("address") != null && !((String) result.get("address")).isEmpty()) {
            int addressCount = (int) stats.getOrDefault("addressesFound", 0);
            stats.put("addressesFound", addressCount + 1);
        }
    }

    private Map<String, Object> mergeResults(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new HashMap<>();

        // Объединяем статистику
        merged.put("totalBatches",
                (int) left.getOrDefault("totalBatches", 1) +
                        (int) right.getOrDefault("totalBatches", 1));

        merged.put("processedCount",
                (int) left.getOrDefault("processedCount", 0) +
                        (int) right.getOrDefault("processedCount", 0));

        // Объединяем счетчики
        merged.put("successCount",
                (int) left.getOrDefault("successCount", 0) +
                        (int) right.getOrDefault("successCount", 0));

        merged.put("totalResponseTime",
                (long) left.getOrDefault("totalResponseTime", 0L) +
                        (long) right.getOrDefault("totalResponseTime", 0L));

        merged.put("emailsFound",
                (int) left.getOrDefault("emailsFound", 0) +
                        (int) right.getOrDefault("emailsFound", 0));

        merged.put("phonesFound",
                (int) left.getOrDefault("phonesFound", 0) +
                        (int) right.getOrDefault("phonesFound", 0));

        merged.put("addressesFound",
                (int) left.getOrDefault("addressesFound", 0) +
                        (int) right.getOrDefault("addressesFound", 0));

        // Объединяем результат
        List<Map<String, Object>> allResults = new ArrayList<>();
        allResults.addAll((List<Map<String, Object>>) left.getOrDefault("results", Collections.emptyList()));
        allResults.addAll((List<Map<String, Object>>) right.getOrDefault("results", Collections.emptyList()));
        merged.put("results", allResults);

        return merged;
    }
}
