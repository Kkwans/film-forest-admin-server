package com.filmforest.stats.controller;

final class CsvCellSanitizer {

    private static final String FORMULA_PREFIXES = "=+-@";

    private CsvCellSanitizer() {
    }

    static String escape(Object rawValue) {
        if (rawValue == null) {
            return "";
        }

        String value = String.valueOf(rawValue);
        if (startsLikeFormula(value)) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static boolean startsLikeFormula(String value) {
        if (!value.isEmpty() && (value.charAt(0) == '\t' || value.charAt(0) == '\r' || value.charAt(0) == '\n')) {
            return true;
        }
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        if (index == value.length()) {
            return false;
        }
        char first = value.charAt(index);
        return FORMULA_PREFIXES.indexOf(first) >= 0;
    }
}
