package com.utmn.chamortsev.urlparser.core;

import org.testng.annotations.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContactExtractorTest {

    @Test
    void extractsContactsFromHtml() {
        // given
        ContactExtractor extractor = new ContactExtractor();
        String html = """
            <html>
                <p>Contact: user@example.com</p>
                <p>Phone: +7 (999) 123-45-67</p>
            </html>""";

        // when
        ContactAnalysis result = extractor.extractContacts(html);

        // then
        assertEquals(6, result.qualityScore());
        assertTrue(result.contacts().containsKey("email"));
        assertTrue(result.contacts().containsKey("phone"));
    }
}
