/*
 * Copyright 2026 Maxime Ethier - Consultant en Bioinformatique/Biocomputing Consultant
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bioinfo.mabscope.ms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * A table read from a delimited text file, as exported by the MS software: tab-separated (MaxQuant,
 * FragPipe), comma-separated (Skyline) or semicolon-separated (spreadsheets in French locales).
 *
 * <p>The delimiter is deduced from the header line. Fields may be enclosed in double quotes, a
 * doubled quote standing for a quote; a quoted field cannot span several lines. Blank lines are
 * ignored. Columns are looked up by name, ignoring case and surrounding spaces.
 */
public final class DelimitedTable {

    private final List<String> header;
    private final Map<String, Integer> index;
    private final List<Row> rows;

    private DelimitedTable(List<String> header, List<List<String>> values, List<Integer> lines) {
        this.header = List.copyOf(header);
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            columns.putIfAbsent(key(header.get(i)), i);
        }
        this.index = Map.copyOf(columns);
        List<Row> built = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            built.add(new Row(index, lines.get(i), values.get(i)));
        }
        this.rows = List.copyOf(built);
    }

    /**
     * Reads a whole table.
     *
     * @param reader the text; it is not closed
     * @return the table, without rows when the text only holds a header
     * @throws IOException if the text cannot be read
     * @throws IllegalArgumentException if the text holds no header line
     */
    public static DelimitedTable read(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        BufferedReader lines = new BufferedReader(reader);
        List<String> header = null;
        char delimiter = ',';
        List<List<String>> values = new ArrayList<>();
        List<Integer> lineNumbers = new ArrayList<>();
        int lineNumber = 0;
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            lineNumber++;
            if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }
            if (line.isBlank()) {
                continue;
            }
            if (header == null) {
                delimiter = delimiterOf(line);
                header = split(line, delimiter);
            } else {
                values.add(split(line, delimiter));
                lineNumbers.add(lineNumber);
            }
        }
        if (header == null) {
            throw new IllegalArgumentException("The file is empty");
        }
        return new DelimitedTable(header, values, lineNumbers);
    }

    /**
     * Reads a table held in memory.
     *
     * @param text the content of the file
     * @return the table
     * @throws IllegalArgumentException if the text holds no header line
     */
    public static DelimitedTable parse(String text) {
        try {
            return read(new StringReader(Objects.requireNonNull(text, "text")));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the column names, in file order. */
    public List<String> header() {
        return header;
    }

    /** Returns the data rows, in file order. */
    public List<Row> rows() {
        return rows;
    }

    /** Tells whether the table has the given column, ignoring case. */
    public boolean hasColumn(String column) {
        return index.containsKey(key(column));
    }

    private static String key(String column) {
        return column.strip().toLowerCase(Locale.ROOT);
    }

    private static char delimiterOf(String headerLine) {
        if (headerLine.indexOf('\t') >= 0) {
            return '\t';
        }
        if (headerLine.indexOf(';') >= 0 && headerLine.indexOf(',') < 0) {
            return ';';
        }
        return ',';
    }

    private static List<String> split(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c != '"') {
                    field.append(c);
                } else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == delimiter) {
                fields.add(field.toString().strip());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().strip());
        return fields;
    }

    /** A data row of the table. */
    public static final class Row {

        private final Map<String, Integer> index;
        private final int line;
        private final List<String> values;

        private Row(Map<String, Integer> index, int line, List<String> values) {
            this.index = index;
            this.line = line;
            this.values = List.copyOf(values);
        }

        /** Returns the 1-based line number of the row in the file, for error messages. */
        public int line() {
            return line;
        }

        /**
         * Returns the value of a column.
         *
         * @param column column name, in any case
         * @return the stripped value; empty when the table or the row lacks the column
         */
        public String get(String column) {
            Integer i = index.get(key(column));
            return i == null || i >= values.size() ? "" : values.get(i);
        }

        /**
         * Returns the value of the first column of {@code columns} that the table has.
         *
         * @param columns alternative column names, by order of preference
         * @return the value; empty when the table has none of the columns
         */
        public String first(List<String> columns) {
            for (String column : columns) {
                if (index.containsKey(key(column))) {
                    return get(column);
                }
            }
            return "";
        }
    }
}
