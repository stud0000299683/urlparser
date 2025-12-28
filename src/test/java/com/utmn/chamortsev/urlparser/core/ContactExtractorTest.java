package com.utmn.chamortsev.urlparser.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactExtractorTest {

    private final ContactExtractor extractor = new ContactExtractor();

    @Test
    void extractsEmails() {
        String html = "<html><p>Email: test@example.com</p></html>";
        ContactAnalysis result = extractor.extractContacts(html);

        assertEquals(3, result.qualityScore());
        assertEquals("test@example.com", result.contacts().get("email"));
    }

    @Test
    void extractsPhones() {
        // ТЕСТОВЫЕ НОМЕРА
        String html = "<html><p>Phone: +7(999)123-45-67</p></html>";
        ContactAnalysis result = extractor.extractContacts(html);

        assertEquals(3, result.qualityScore());
        assertTrue(result.contacts().containsKey("phone"));
        assertTrue(result.contacts().get("phone").contains("999"));
    }

    @Test
    void fullContactExtraction() {
        String html = """
            <html>
                <p>Contact: user@test.com</p>
                <p>Tel: +7 999 123-45-67</p>
            </html>""";

        ContactAnalysis result = extractor.extractContacts(html);

        assertEquals(6, result.qualityScore());  // email(3) + phone(3)
        assertEquals(2, result.totalContacts());
        assertTrue(result.contacts().containsKey("email"));
        assertTrue(result.contacts().containsKey("phone"));
    }

    @Test
    void emptyContent() {
        ContactAnalysis result = extractor.extractContacts("");
        assertEquals(0, result.qualityScore());
        assertEquals(0, result.totalContacts());
        assertTrue(result.contacts().isEmpty());
    }
}
