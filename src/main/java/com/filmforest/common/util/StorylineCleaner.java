package com.filmforest.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class StorylineCleaner {

    private static final String DELIMITER = "\u0000";
    private static final Pattern BRACKETED_CONTROL = Pattern.compile(
            "[\\[【（(]\\s*(?:展开全部|收起部分|收起简介|展开简介|收起全文|展开全文|查看更多|展开更多|点击展开|展开|收起|更多)\\s*[\\]】）)]");
    private static final Pattern EXPLICIT_CONTROL = Pattern.compile(
            "(?:展开全部|收起部分|收起简介|展开简介|收起全文|展开全文|查看更多|展开更多|点击展开)");
    private static final Pattern TRAILING_COLLAPSE = Pattern.compile("收起\\s*$");
    private static final Pattern TRAILING_ELLIPSIS = Pattern.compile("(?:…+\\.?|\\.{3,})\\s*$");

    private StorylineCleaner() {
    }

    public static String clean(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? null : "";
        }

        String delimited = BRACKETED_CONTROL.matcher(text).replaceAll(DELIMITER);
        delimited = EXPLICIT_CONTROL.matcher(delimited).replaceAll(DELIMITER);
        delimited = TRAILING_COLLAPSE.matcher(delimited).replaceAll(DELIMITER);
        boolean containedControl = delimited.indexOf('\u0000') >= 0;

        List<String> segments = new ArrayList<>();
        for (String rawSegment : delimited.split(DELIMITER, -1)) {
            String segment = normalizeWhitespace(rawSegment);
            if (containedControl) {
                segment = TRAILING_ELLIPSIS.matcher(segment).replaceFirst("").trim();
            }
            if (!segment.isBlank()) {
                mergeSegment(segments, segment);
            }
        }
        return String.join(" ", segments).trim();
    }

    private static void mergeSegment(List<String> segments, String candidate) {
        for (int index = 0; index < segments.size(); index++) {
            String existing = segments.get(index);
            if (existing.equals(candidate) || existing.contains(candidate)) {
                return;
            }
            if (candidate.contains(existing)) {
                segments.set(index, candidate);
                return;
            }
        }
        segments.add(candidate);
    }

    private static String normalizeWhitespace(String value) {
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
}
