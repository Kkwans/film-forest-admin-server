package com.filmforest.stats.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvCellSanitizerTest {

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertThat(CsvCellSanitizer.escape("=HYPERLINK(\"https://example.test\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"https://example.test\"\")\"");
        assertThat(CsvCellSanitizer.escape("  +1+1"))
                .isEqualTo("'  +1+1");
        assertThat(CsvCellSanitizer.escape("@SUM(A1:A2)"))
                .isEqualTo("'@SUM(A1:A2)");
        assertThat(CsvCellSanitizer.escape("\tcmd"))
                .isEqualTo("\"'\tcmd\"");
    }

    @Test
    void escapesCsvDelimitersAndQuotes() {
        assertThat(CsvCellSanitizer.escape("标题,\"副标题\""))
                .isEqualTo("\"标题,\"\"副标题\"\"\"");
    }

    @Test
    void keepsOrdinaryValuesUnchanged() {
        assertThat(CsvCellSanitizer.escape("普通标题")).isEqualTo("普通标题");
        assertThat(CsvCellSanitizer.escape(null)).isEmpty();
    }
}
