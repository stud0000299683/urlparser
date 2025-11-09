# URL Parser Application

Spring Boot приложение для парсинга контактной информации с веб-сайтов. Приложение предоставляет REST API для добавления URL, асинхронной обработки и извлечения контактных данных (email, телефоны, адреса, рабочие часы).

## 🚀 Основные возможности

- **Добавление URL** для обработки (одиночно и пакетно)
- **Асинхронная обработка** с использованием ThreadPoolExecutor и CompletableFuture
- **Рекурсивная обработка** через ForkJoinPool с разбиением на батчи
- **Извлечение контактной информации**:
  - Email адреса
  - Номера телефонов
  - Физические адреса
  - Время работы
- **Статистика и мониторинг** процесса обработки
- **REST API** с документацией Swagger/OpenAPI
- **In-memory база данных** H2 с веб-консолью

## 🛠 Технологический стек

### Основные зависимости (pom.xml)

```xml
<!-- Spring Boot Starter Web -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Spring Boot Starter Data JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- H2 Database -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <scope>provided</scope>
</dependency>

<!-- Swagger/OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

## Версии технологий
Java: 21

Spring Boot: 3.5.5

H2 Database: версия из Spring Boot BOM

Swagger/OpenAPI: 2.3.0

Lombok: последняя стабильная версия

## 📋 Требования к системе
Java 21 (требуется для совместимости с Spring Boot 3.5.5)

Maven 3.6+

Минимум 512MB оперативной памяти

Доступ в интернет для загрузки зависистей

## 🏃 Быстрый старт
1. Клонирование и сборка
```
git clone <repository-url>
cd urlparser
mvn clean package
```

2. Запуск приложения
```
mvn spring-boot:run
```

## Веб-интерфейсы приложения
После запуска доступны следующие интерфейсы:

| Интерфейс | URL | Назначение              |
|-------|----------|-------------------------|
| Swagger UI | `http://localhost:8080/swagger-ui.html` | SWAGGER документация    |
| H2 Console | `http://localhost:8080/h2-console` | Управление базой данных |
| REST API | `http://localhost:8080/api/*` | Endpoints приложения    |
	
Данные для подключения к H2 Console:

JDBC URL: jdbc:h2:mem:urlparserdb

Username: sa

Password: (оставить пустым)

## 📚 API Endpoints

### Основные endpoints управления URL

| Метод | Endpoint | Описание | Требуемые параметры |
|-------|----------|-----------|---------------------|
| GET | `/api/urls` | Получить все URLs | - |
| POST | `/api/urls` | Добавить новый URL | `url`, `name`, `description` |
| POST | `/api/urls/batch` | Пакетное добавление URLs | Массив объектов URL |
| POST | `/api/urls/process` | Запуск обработки всех URL | - |
| GET | `/api/urls/results` | Получить результаты обработки | - |
| GET | `/api/urls/statistics` | Статистика обработки | - |
| GET | `/api/urls/thread-pool-info` | Информация о пуле потоков | - |
| PUT | `/api/urls/{id}` | Обновить URL | `id` в пути, тело запроса |
| DELETE | `/api/urls/{id}` | Удалить URL | `id` в пути |

### Асинхронные endpoints

| Метод | Endpoint | Описание | Особенности |
|-------|----------|-----------|-------------|
| POST | `/api/async/process` | Асинхронная обработка | CompletableFuture |
| POST | `/api/async/process/forkjoin` | ForkJoin обработка | Рекурсивная обработка |
| GET | `/api/async/status` | Статус обработки | Мониторинг потоков |
| GET | `/api/async/results/enhanced` | Расширенные результаты | Детальная аналитика |
| GET | `/api/async/compare-methods` | Сравнение методов | Benchmark обработки |
## 💡 Примеры использования API

#Добавление одиночного URL
```
curl -X POST "http://localhost:8080/api/urls" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://example.com",
    "name": "Example Site",
    "description": "Test website for contact parsing"
  }'
```

# Пакетное добавление URL
```
curl -X POST "http://localhost:8080/api/urls/batch" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "url": "https://company1.com",
      "name": "Company One",
      "description": "First test company"
    },
    {
      "url": "https://company2.com", 
      "name": "Company Two",
      "description": "Second test company"
    }
  ]'
```
# Запуск обработки URL
```
curl -X POST "http://localhost:8080/api/urls/process"
```

# Получение статистики
```
curl -X GET "http://localhost:8080/api/urls/statistics"
```

# Использование ForkJoin обработки
```
curl -X POST "http://localhost:8080/api/async/process/forkjoin"
```
## 🔧 Конфигурация приложения
Настройки пулов потоков
```
// ThreadPoolExecutor для базовой асинхронной обработки
THREAD_POOL_SIZE = 5
// ForkJoinPool для рекурсивной обработки  
FORK_JOIN_PARALLELISM = 8
// Размер батча для ForkJoin
BATCH_SIZE = 3
// Таймаут HTTP запросов
TIMEOUT = Duration.ofSeconds(10)
```

##  📊 Мониторинг и метрики
Приложение предоставляет детальную статистику через эндпоинт /api/urls/statistics:

Общие метрики: количество URL, активные URL, общее количество результатов

Производительность: успешные запросы, среднее время ответа, процент успеха

Мониторинг потоков: активные потоки, размер очереди, завершенные задачи

Эффективность парсинга: количество извлеченных контактов по типам

## 📁 Структура проекта
```plaintext
src/main/java/ru/utmn/chamortsev/urlparser/
├── config/
│   ├── DataInitializer.java      # Инициализация при запуске
│   └── SwaggerConfig.java        # Конфигурация Swagger
├── controller/
│   ├── UrlController.java        # Основные endpoints
│   └── AsyncUrlController.java   # Асинхронные endpoints
├── entity/
│   ├── UrlEntity.java           # Сущность URL
│   └── UrlResultEntity.java     # Сущность результата
├── repository/
│   ├── UrlRepository.java       # Репозиторий URL
│   └── UrlResultRepository.java # Репозиторий результатов
├── service/
│   └── UrlProcessingService.java # Бизнес-логика обработки
├── dto/
│   ├── UrlRequest.java          # DTO запроса URL
│   ├── UrlUpdateRequest.java    # DTO обновления URL
│   └── AsyncProcessingResult.java # DTO асинхронного результата
└── UrlParserApplication.java    # Главный класс приложения
```