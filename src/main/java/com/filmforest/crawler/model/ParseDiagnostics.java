package com.filmforest.crawler.model;

import java.util.List;
import java.util.Map;

public record ParseDiagnostics(
        List<String> matchedSelectors,
        List<String> missingRequiredFields,
        List<String> warnings,
        String pageFingerprint,
        Map<String, Integer> resourceCounts,
        Map<ParsedResource.Kind, ResourceParseStatus> resourceStatuses
) {
    public ParseDiagnostics {
        matchedSelectors = matchedSelectors == null ? List.of() : List.copyOf(matchedSelectors);
        missingRequiredFields = missingRequiredFields == null ? List.of() : List.copyOf(missingRequiredFields);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        resourceCounts = resourceCounts == null ? Map.of() : Map.copyOf(resourceCounts);
        resourceStatuses = resourceStatuses == null ? Map.of() : Map.copyOf(resourceStatuses);
    }

    /**
     * Compatibility constructor for callers that predate per-kind statuses.
     */
    public ParseDiagnostics(List<String> matchedSelectors,
                            List<String> missingRequiredFields,
                            List<String> warnings,
                            String pageFingerprint,
                            Map<String, Integer> resourceCounts) {
        this(matchedSelectors, missingRequiredFields, warnings, pageFingerprint,
                resourceCounts, Map.of());
    }
}
