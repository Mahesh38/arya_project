package com.smartgrievance.complaint;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Minimal RFC 4180 writer for the admin export (R-32). Cells that a spreadsheet could run as a formula, that is those
 * starting with {@code = + - @}, tab or carriage return, get a leading apostrophe (D43, D75, doc 11 §8.3). Every cell is
 * quoted and inner quotes are doubled, so commas, quotes and line breaks stay inside their cell.
 */
public final class CsvWriter {
    public static final char BOM = '﻿';
    private final Writer out;

    public CsvWriter(Writer out) {
        this.out = out;
    }

    public CsvWriter bom() throws IOException {
        out.write(BOM);
        return this;
    }

    public void row(List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) out.write(',');
            out.write(cell(cells.get(i)));
        }
        out.write("\r\n");
    }

    public static String cell(String value) {
        if (value == null || value.isEmpty()) return "\"\"";
        String safe = needsPrefix(value.charAt(0)) ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static boolean needsPrefix(char first) {
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
