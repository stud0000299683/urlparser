package com.utmn.chamortsev.urlparser.ports;

import java.util.concurrent.CompletableFuture;

public interface ContentFetcher {
    CompletableFuture<String> fetchContent(String url);
}
