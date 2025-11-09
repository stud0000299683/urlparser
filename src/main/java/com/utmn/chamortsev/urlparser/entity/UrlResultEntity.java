package com.utmn.chamortsev.urlparser.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "url_results")
@Schema(description = "Result of URL parsing operation")
@Getter
@Setter
public class UrlResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "Unique identifier of the result", example = "1")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    @JsonIgnore
    @Schema(description = "Reference to the parsed URL")
    private UrlEntity urlEntity;

    @Schema(description = "HTTP status code", example = "200")
    private Integer statusCode;

    @Schema(description = "Response time in milliseconds", example = "350")
    private Long responseTime;

    @Schema(description = "Extracted address information", example = "ул. Примерная, 123")
    private String address;

    @Schema(description = "Extracted phone numbers", example = "+7 (123) 456-78-90")
    private String phone;

    @Schema(description = "Extracted email addresses", example = "contact@example.com")
    private String email;

    @Schema(description = "Extracted working hours", example = "пн-пт 9:00-18:00")
    private String workingHours;

    @Schema(description = "Error message if processing failed")
    private String errorMessage;

    @Schema(description = "Processing timestamp")
    private LocalDateTime processedAt;

    @Column(length = 1000)
    private String notes;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }

    public UrlResultEntity() {}

    public UrlResultEntity(UrlEntity urlEntity, Integer statusCode, Long responseTime) {
        this.urlEntity = urlEntity;
        this.statusCode = statusCode;
        this.responseTime = responseTime;
    }


//    public Long getId() { return id; }
//    public void setId(Long id) { this.id = id; }
//
//    public UrlEntity getUrlEntity() { return urlEntity; }
//    public void setUrlEntity(UrlEntity urlEntity) { this.urlEntity = urlEntity; }
//
//    public Integer getStatusCode() { return statusCode; }
//    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
//
//    public Long getResponseTime() { return responseTime; }
//    public void setResponseTime(Long responseTime) { this.responseTime = responseTime; }
//
//    public String getAddress() { return address; }
//    public void setAddress(String address) { this.address = address; }
//
//    public String getPhone() { return phone; }
//    public void setPhone(String phone) { this.phone = phone; }
//
//    public String getEmail() { return email; }
//    public void setEmail(String email) { this.email = email; }
//
//    public String getWorkingHours() { return workingHours; }
//    public void setWorkingHours(String workingHours) { this.workingHours = workingHours; }
//
//    public String getErrorMessage() { return errorMessage; }
//    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
//
//    public LocalDateTime getProcessedAt() { return processedAt; }
//    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
//
//    public String getNotes() { return notes; }
//    public void setNotes(String notes) { this.notes = notes; }
}