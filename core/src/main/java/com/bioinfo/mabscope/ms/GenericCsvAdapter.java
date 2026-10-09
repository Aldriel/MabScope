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

import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import java.util.List;

/**
 * Reads a simple table for the other software: a {@code sequence} column in ProForma notation
 * ({@code EEQYN[G0F]STYR}, {@code PEPM[+15.9949]TIDE}), a {@code sample} column and an {@code
 * intensity} column.
 */
public final class GenericCsvAdapter extends TableAdapter {

    private final PeptideSequenceParser parser;

    /**
     * Creates the adapter.
     *
     * @param catalog the modifications known by name, including the glycans
     */
    public GenericCsvAdapter(ModificationCatalog catalog) {
        super(
                MsFormat.GENERIC,
                catalog,
                List.of(List.of("sequence"), List.of("sample"), List.of("intensity")));
        this.parser = new PeptideSequenceParser(catalog.registry());
    }

    @Override
    protected ObservedPeptide convert(DelimitedTable.Row row) {
        double intensity = intensity(row.get("intensity"));
        return new ObservedPeptide(parser.parse(row.get("sequence")), row.get("sample"), intensity);
    }
}
