package com.utmn.chamortsev.urlparser.core;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Component
public class ContactExtractor {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?\\d{1,3})?\\s*\\(?(\\d{3})\\)?\\s*[-.\\s]?(\\d{3})\\s*[-.\\s]?(\\d{2,4})"
    );

    public ContactAnalysis extractContacts(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return new ContactAnalysis(0, 0, Map.of());
        }

        Map<String, String> contacts = new HashMap<>();

        Set<String> emails = extractEmails(htmlContent);
        Set<String> phones = extractPhones(htmlContent);

        if (!emails.isEmpty()) {
            contacts.put("email", String.join(", ", emails));
        }
        if (!phones.isEmpty()) {
            contacts.put("phone", String.join(", ", phones));
        }

        return new ContactAnalysis(
                calculateQualityScore(contacts),
                contacts.size(),
                contacts
        );
    }

    private Set<String> extractEmails(String content) {
        return extractWithPattern(EMAIL_PATTERN, content);
    }

    private Set<String> extractPhones(String content) {
        return extractWithPattern(PHONE_PATTERN, content);
    }

    private Set<String> extractWithPattern(Pattern pattern, String content) {
        Set<String> results = new HashSet<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            results.add(matcher.group());
        }
        return results;
    }

    private int calculateQualityScore(Map<String, String> contacts) {
        int score = 0;
        if (contacts.containsKey("email") && !contacts.get("email").isEmpty()) {
            score += 3;
        }
        if (contacts.containsKey("phone") && !contacts.get("phone").isEmpty()) {
            score += 3;
        }
        return score;
    }
}
