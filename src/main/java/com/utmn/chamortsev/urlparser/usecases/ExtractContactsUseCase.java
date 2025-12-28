package com.utmn.chamortsev.urlparser.usecases;

import com.utmn.chamortsev.urlparser.core.ContactExtractor;
import com.utmn.chamortsev.urlparser.ports.ContactPublisher;
import com.utmn.chamortsev.urlparser.ports.ContentFetcher;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ExtractContactsUseCase {

    private final ContactExtractor extractor;
    private final ContentFetcher fetcher;
    private final ContactPublisher publisher;

    public ExtractContactsUseCase(
            ContactExtractor extractor,
            ContentFetcher fetcher,
            ContactPublisher publisher) {
        this.extractor = extractor;
        this.fetcher = fetcher;
        this.publisher = publisher;
    }

    public CompletableFuture<Void> execute(String url) {
        return fetcher.fetchContent(url)
                .thenApply(extractor::extractContacts)
                .thenAccept(analysis -> publisher.publish(url, analysis));
    }
}
