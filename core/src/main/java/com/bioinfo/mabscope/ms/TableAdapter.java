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

import com.bioinfo.proteomics.parser.ParseIssue;
import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.ParsingException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base of the adapters (Template Method pattern): checks the columns, converts each row with {@link
 * #convert} and reports the faulty rows, so that each adapter only knows its own columns.
 *
 * <p>Rows without intensity, decoys and contaminants are normal in result files: they are counted
 * in a single warning per reason instead of one issue per row.
 */
abstract class TableAdapter implements MsResultAdapter {

    /** Catalog resolving the modification names and masses. */
    protected final ModificationCatalog catalog;

    private final MsFormat format;
    private final List<List<String>> requiredColumns;

    /**
     * Creates an adapter.
     *
     * @param format the format handled
     * @param catalog resolves the modifications
     * @param requiredColumns groups of alternative columns; the table needs one column per group
     */
    TableAdapter(MsFormat format, ModificationCatalog catalog, List<List<String>> requiredColumns) {
        this.format = format;
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.requiredColumns = List.copyOf(requiredColumns);
    }

    @Override
    public final MsFormat format() {
        return format;
    }

    @Override
    public final boolean recognizes(DelimitedTable table) {
        return missingColumns(table).isEmpty();
    }

    @Override
    public final ParseResult<ObservedPeptide> adapt(DelimitedTable table) {
        List<String> missing = missingColumns(table);
        if (!missing.isEmpty()) {
            throw new ParsingException(
                    "header",
                    format.label() + " columns are missing: " + String.join(", ", missing));
        }
        List<ObservedPeptide> peptides = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();
        Map<String, Integer> skipped = new LinkedHashMap<>();
        for (DelimitedTable.Row row : table.rows()) {
            try {
                peptides.add(convert(row));
            } catch (SkippedRow s) {
                skipped.merge(s.getMessage(), 1, Integer::sum);
            } catch (IllegalArgumentException | ParsingException e) {
                issues.add(
                        new ParseIssue(
                                ParseIssue.Severity.ERROR, "line " + row.line(), e.getMessage()));
            }
        }
        skipped.forEach(
                (reason, count) ->
                        issues.add(
                                new ParseIssue(
                                        ParseIssue.Severity.WARNING,
                                        "rows",
                                        count + " row(s) skipped: " + reason)));
        return new ParseResult<>(peptides, issues);
    }

    /**
     * Converts one row.
     *
     * @param row the row
     * @return the observed peptide
     * @throws SkippedRow if the row is legitimately ignored, such as a decoy
     * @throws IllegalArgumentException if the row is invalid
     */
    protected abstract ObservedPeptide convert(DelimitedTable.Row row);

    /**
     * Reads an intensity; a missing or null intensity means that the peptide was not quantified.
     *
     * @param text the value of the cell
     * @return the intensity, strictly positive
     * @throws SkippedRow if the cell is empty, {@code NaN}, {@code #N/A} or zero
     * @throws IllegalArgumentException if the cell is not a number or is negative
     */
    protected static double intensity(String text) {
        String value = text.strip();
        if (value.isEmpty() || value.equalsIgnoreCase("NaN") || value.equalsIgnoreCase("#N/A")) {
            throw new SkippedRow("no intensity");
        }
        if (value.indexOf(',') >= 0 && value.indexOf('.') < 0) {
            value = value.replace(',', '.');
        }
        double intensity;
        try {
            intensity = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid intensity '" + text + "'");
        }
        if (intensity < 0 || !Double.isFinite(intensity)) {
            throw new IllegalArgumentException("Invalid intensity '" + text + "'");
        }
        if (intensity == 0) {
            throw new SkippedRow("no intensity");
        }
        return intensity;
    }

    private List<String> missingColumns(DelimitedTable table) {
        List<String> missing = new ArrayList<>();
        for (List<String> group : requiredColumns) {
            if (group.stream().noneMatch(table::hasColumn)) {
                missing.add(String.join(" or ", group));
            }
        }
        return missing;
    }

    /** Signals a row that is ignored on purpose; the message is the reason. */
    static final class SkippedRow extends RuntimeException {

        private static final long serialVersionUID = 1L;

        SkippedRow(String reason) {
            super(reason, null, false, false);
        }
    }
}
