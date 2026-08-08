package com.filmforest.crawler.model;

import java.util.List;
import java.util.Map;

public record ParseDiagnostics(
        List<String> matchedSelectors,
        List<String> missingRequiredFields,
        List<String> warnings,
        String pageFingerprint,
        Map<String, Integer> resourceCounts
) {
}
