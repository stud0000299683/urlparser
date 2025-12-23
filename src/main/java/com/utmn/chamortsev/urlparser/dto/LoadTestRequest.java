package com.utmn.chamortsev.urlparser.dto;

import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Schema(description = "Запрос на генерацию тестовых данных")
public class LoadTestRequest {

    @Schema(description = "Количество URL для генерации", example = "50", defaultValue = "50")
    private Integer urlCount = 50;

    @Schema(description = "Количество потоков для параллельной обработки", example = "10", defaultValue = "10")
    private Integer threadCount = 10;

    @Schema(description = "Длительность теста в секундах", example = "60", defaultValue = "60")
    private Integer durationSeconds = 60;

    @Schema(description = "Интервал между запросами (мс)", example = "100", defaultValue = "100")
    private Integer requestIntervalMs = 100;

    @Schema(description = "Тип нагрузки", example = "ASYNC", defaultValue = "ASYNC")
    private String testType = "ASYNC"; // ASYNC, FORKJOIN, SYNC

    @Schema(description = "Генерировать ли тестовые URL автоматически", example = "true", defaultValue = "true")
    private Boolean generateUrls = true;
}