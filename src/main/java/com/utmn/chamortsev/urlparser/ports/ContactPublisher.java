package com.utmn.chamortsev.urlparser.ports;

import com.utmn.chamortsev.urlparser.core.ContactAnalysis;

public interface ContactPublisher {
    void publish(String url, ContactAnalysis analysis);
}
