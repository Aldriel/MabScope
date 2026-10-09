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

import com.bioinfo.proteomics.parser.ParseResult;
import com.bioinfo.proteomics.parser.ParsingException;

import java.io.IOException;
import java.io.Reader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Imports a result file of any supported software, recognizing its format from its columns.
 * Instances are thread-safe.
 */
public final class MsResultImporter {

    private final List<MsResultAdapter> adapters;

    /** Creates an importer for every {@link MsFormat}. */
    public MsResultImporter() {
        this(new ModificationCatalog());
    }

    /**
     * Creates an importer for every {@link MsFormat}.
     *
     * @param catalog resolves the modifications
     */
    public MsResultImporter(ModificationCatalog catalog) {
        // Most specific formats first: the generic columns could appear in another export.
        this.adapters =
                List.of(
                        new MaxQuantAdapter(catalog),
                        new FragPipeAdapter(catalog),
                        new SkylineAdapter(catalog),
                        new GenericCsvAdapter(catalog));
    }

    /**
     * Imports a file whose format is detected.
     *
     * @param reader the content of the file; it is not closed
     * @return the observed peptides
     * @throws IOException if the file cannot be read
     * @throws ParsingException if the file is empty or its format is not recognized
     */
    public MsImport read(Reader reader) throws IOException {
        DelimitedTable table = table(reader);
        MsFormat format =
                detect(table)
                        .orElseThrow(
                                () ->
                                        new ParsingException(
                                                "header",
                                                "Unrecognized file; expected a MaxQuant"
                                                        + " evidence.txt, a FragPipe psm.tsv, a"
                                                        + " Skyline report or sequence, sample and"
                                                        + " intensity columns"));
        return adapt(table, format);
    }

    /**
     * Imports a file of a known format.
     *
     * @param reader the content of the file; it is not closed
     * @param format format of the file
     * @return the observed peptides
     * @throws IOException if the file cannot be read
     * @throws ParsingException if the file is empty or lacks a column of the format
     */
    public MsImport read(Reader reader, MsFormat format) throws IOException {
        Objects.requireNonNull(format, "format");
        return adapt(table(reader), format);
    }

    /**
     * Recognizes the format of a table.
     *
     * @param table the table
     * @return the first format whose columns are all present, if any
     */
    public Optional<MsFormat> detect(DelimitedTable table) {
        return adapters.stream()
                .filter(a -> a.recognizes(table))
                .map(MsResultAdapter::format)
                .findFirst();
    }

    private MsImport adapt(DelimitedTable table, MsFormat format) {
        MsResultAdapter adapter =
                adapters.stream().filter(a -> a.format() == format).findFirst().orElseThrow();
        ParseResult<ObservedPeptide> result = adapter.adapt(table);
        return new MsImport(format, result.items(), result.issues());
    }

    private static DelimitedTable table(Reader reader) throws IOException {
        try {
            return DelimitedTable.read(reader);
        } catch (IllegalArgumentException e) {
            throw new ParsingException("", e.getMessage());
        }
    }
}
