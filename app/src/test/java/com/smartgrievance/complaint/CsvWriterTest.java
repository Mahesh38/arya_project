package com.smartgrievance.complaint;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CsvWriterTest {
    @Test
    @DisplayName("TC-UT-07 CSV cells are escaped against formula injection and quoted")
    void escapesCells() throws IOException {
        for (String risky : List.of("=1+1", "+x", "-x", "@x", "\tx", "\rx")) {
            assertThat(CsvWriter.cell(risky)).isEqualTo("\"'" + risky + "\"");
        }
        assertThat(CsvWriter.cell("a\"b")).isEqualTo("\"a\"\"b\"");
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.cell("पाणी गळती")).isEqualTo("\"पाणी गळती\"");
        assertThat(CsvWriter.cell(null)).isEqualTo("\"\"");
        assertThat(CsvWriter.cell("safe-text")).isEqualTo("\"safe-text\"");

        var out = new StringWriter();
        new CsvWriter(out).bom().row(List.of("a", "=b"));
        assertThat(out.toString()).isEqualTo("﻿\"a\",\"'=b\"\r\n");
    }
}
