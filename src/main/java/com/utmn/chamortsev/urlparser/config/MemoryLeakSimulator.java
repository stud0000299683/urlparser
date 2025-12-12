package com.utmn.chamortsev.urlparser.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class MemoryLeakSimulator {

    private static final List<byte[]> leakStorage = new ArrayList<>();

    @PostConstruct
    public void simulateLeak() {
        new Thread(() -> {
            System.out.println("=== Memory leak simulation started ===");
            while (true) {
                // Добавляем 10MB чанки каждые 500ms
                leakStorage.add(new byte[10 * 1024 * 1024]);
                System.out.printf("Leak storage size: %d MB%n",
                        leakStorage.size() * 10 / 1024);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "Memory-Leak-Thread").start();
    }

    public static long getLeakSizeMB() {
        return leakStorage.size() * 10L / 1024;
    }
}
