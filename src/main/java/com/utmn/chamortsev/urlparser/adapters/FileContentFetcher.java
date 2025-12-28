package com.utmn.chamortsev.urlparser.adapters;

import com.utmn.chamortsev.urlparser.ports.ContentFetcher;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component("fileFetcher")
public class FileContentFetcher implements ContentFetcher {

    @Override
    public CompletableFuture<String> fetchContent(String url) {
        return CompletableFuture.completedFuture("""
            <html>
                <p>Email: test@example.com</p>
                <p>Phone: +7 (999) 123-45-67</p>
                <p>Address: ул. Тестовая, 123</p>
            </html>""");
    }
}
