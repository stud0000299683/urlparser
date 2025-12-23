package com.utmn.chamortsev.urlparser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Override
    public void run(String... args) throws Exception {
        logger.info("=== URL Parser Начало работы ===");
        logger.info("H2 Console: http://localhost:8080/h2-console");
        logger.info("JDBC URL: jdbc:h2:mem:urlparserdb");
        logger.info("Username: sa, Password: (empty)");
        logger.info("");
        logger.info("=== Основные API Endpoints ===");
        logger.info("GET  /api/urls              - Список всех URLs");
        logger.info("POST /api/urls              - Добавить новый URL");
        logger.info("POST /api/urls/batch        - Добавить несколько URLs");
        logger.info("POST /api/urls/process      - Обработать все URLs");
        logger.info("GET  /api/urls/results      - Вывести результат");
        logger.info("GET  /api/urls/statistics   - Статистика");
        logger.info("GET  /api/urls/thread-pool-info - Информация по потокам");
        logger.info("PUT  /api/urls/{id}         - Обновить URL");
        logger.info("DELETE /api/urls/{id}       - Удалить URL");
        logger.info("");
        logger.info("=== Асинхронные операции ===");
        logger.info("GET /api/async/process       - Асинхронная обработка всех URL");
        logger.info("GET /api/async/process/forkjoin - ForkJoin обработка URL");
        logger.info("GET /api/async/status       - Статус обработки");
        logger.info("GET /api/async/results/enhanced - Статистика асинхронной обработки");
        logger.info("");
        logger.info("=== НАГРУЗОЧНОЕ ТЕСТИРОВАНИЕ ===");
        logger.info("POST /api/loadtest/generate-urls?count=50 - Сгенерировать тестовые URL");
        logger.info("POST /api/loadtest/start     - Запустить нагрузочный тест");
        logger.info("GET  /api/loadtest/status/{id} - Статус теста");
        logger.info("GET  /api/loadtest/active    - Активные тесты");
        logger.info("POST /api/loadtest/quick-start?type=ASYNC - Быстрый старт");
        logger.info("POST /api/loadtest/stop-all  - Остановить все тесты");
        logger.info("");
        logger.info("=== Мониторинг ===");
        logger.info("GET /actuator/health        - Health check");
        logger.info("GET /actuator/metrics       - Метрики приложения");
        logger.info("GET /actuator/prometheus    - Prometheus метрики");
        logger.info("");
        logger.info("=== Веб-интерфейсы ===");
        logger.info("Swagger UI: http://localhost:8080/swagger-ui.html");
        logger.info("Prometheus: http://localhost:9090");
        logger.info("Grafana:    http://localhost:3000 (admin/admin)");
        logger.info("Jaeger:     http://localhost:16686");
        logger.info("");

    }
}