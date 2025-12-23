package com.utmn.chamortsev.urlparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@SpringBootApplication
@EnableCaching
public class UrlParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlParserApplication.class, args);
        System.out.println("=== URL Parser Application started with Memory Leak Simulator ===");
        System.out.println("GC logs: gc.log | Heap: 512MB | Leak active");
        System.out.println("Swagger: http://localhost:8080/swagger-ui.html");
    }
}
