package com.utmn.chamortsev.urlparser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Map;

@Getter
@Setter
@Schema(description = "Результат асинхронной обработки URL")
public class AsyncProcessingResult {

    @Schema(description = "ID URL")
    private Long urlId;

    @Schema(description = "URL адрес")
    private String url;

    @Schema(description = "Название сайта")
    private String name;

    @Schema(description = "Успешность обработки")
    private Boolean success;

    @Schema(description = "HTTP статус код")
    private Integer statusCode;

    @Schema(description = "Время ответа в мс")
    private Long responseTime;

    @Schema(description = "Производительность")
    private String performance;

    @Schema(description = "Оценка качества данных")
    private Integer dataQualityScore;

    @Schema(description = "Общая оценка")
    private Double overallScore;

    @Schema(description = "Общий рейтинг")
    private String overallRating;

    @Schema(description = "Контактные данные")
    private Map<String, String> contacts;

    @Schema(description = "Дополнительная информация")
    private Map<String, Object> additionalInfo;

    @Schema(description = "Время обработки")
    private Date processedAt;

    @Schema(description = "Сообщение об ошибке")
    private String error;
}
