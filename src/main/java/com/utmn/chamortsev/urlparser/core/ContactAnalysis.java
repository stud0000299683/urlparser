package com.utmn.chamortsev.urlparser.core;

import java.util.Map;

public record ContactAnalysis(int qualityScore, int totalContacts, Map<String, String> contacts) {}
