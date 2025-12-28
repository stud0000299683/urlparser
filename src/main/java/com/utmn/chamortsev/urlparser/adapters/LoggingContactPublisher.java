package com.utmn.chamortsev.urlparser.adapters;

import com.utmn.chamortsev.urlparser.core.ContactAnalysis;
import com.utmn.chamortsev.urlparser.ports.ContactPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingContactPublisher implements ContactPublisher {

    private static final Logger logger = LoggerFactory.getLogger(LoggingContactPublisher.class);

    @Override
    public void publish(String url, ContactAnalysis analysis) {
        if (analysis == null || analysis.contacts() == null) {
            logger.warn("No contacts found for URL: {}", url);
            return;
        }

        String emails = analysis.contacts().getOrDefault("email", "");
        String phones = analysis.contacts().getOrDefault("phone", "");

        logger.info("=== Contact Analysis for {} ===", url);
        logger.info("Quality Score: {}", analysis.qualityScore());
        logger.info("Total Contacts: {}", analysis.totalContacts());

        if (!emails.isEmpty()) {
            logger.info("Emails found: {}", emails);
        } else {
            logger.info("No emails found");
        }

        if (!phones.isEmpty()) {
            logger.info("Phones found: {}", phones);
        } else {
            logger.info("No phones found");
        }

        logger.info("================================");
    }
}