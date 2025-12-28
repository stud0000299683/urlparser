package com.utmn.chamortsev.urlparser.adapters;

import com.utmn.chamortsev.urlparser.ports.ContentFetcher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

@Component
@Primary
public class HttpContentFetcher implements ContentFetcher {

    private final RestTemplate restTemplate;

    public HttpContentFetcher() {
        this.restTemplate = new RestTemplate();
    }

    @Override
    public CompletableFuture<String> fetchContent(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return restTemplate.getForObject(url, String.class);
            } catch (Exception e) {
                return "<html><body>No content: " + e.getMessage() + "</body></html>";
            }
        });
    }
}
