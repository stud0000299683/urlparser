package com.utmn.chamortsev.urlparser.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Запрос на обновление URL")
public class UrlUpdateRequest {

    @Schema(description = "Новое название сайта", example = "Обновленное название")
    private String name;

    @Schema(description = "Новое описание сайта", example = "Обновленное описание")
    private String description;

    @Schema(description = "Активен ли URL", example = "true")
    private Boolean active;

    public UrlUpdateRequest() {}

    public UrlUpdateRequest(String name, String description, Boolean active) {
        this.name = name;
        this.description = description;
        this.active = active;
    }

}
