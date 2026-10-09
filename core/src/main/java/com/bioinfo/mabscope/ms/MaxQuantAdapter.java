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

import java.util.List;

/**
 * Reads the {@code evidence.txt} file of MaxQuant: one row per peptide feature, with its modified
 * sequence such as {@code _(Acetyl (Protein N-term))PEPM(Oxidation (M))TIDE_}. The sample is the
 * experiment when experiments are defined, otherwise the raw file. Decoys and contaminants are
 * skipped.
 */
public final class MaxQuantAdapter extends TableAdapter {

    private static final String SEQUENCE = "Modified sequence";
    private static final String INTENSITY = "Intensity";
    private static final List<String> SAMPLE = List.of("Experiment", "Raw file");

    private final BracketNotation notation;

    /**
     * Creates the adapter.
     *
     * @param catalog resolves the MaxQuant modification names
     */
    public MaxQuantAdapter(ModificationCatalog catalog) {
        super(MsFormat.MAXQUANT, catalog, List.of(List.of(SEQUENCE), List.of(INTENSITY), SAMPLE));
        this.notation =
                new BracketNotation(
                        '(',
                        ')',
                        name ->
                                catalog.byName(name)
                                        .orElseThrow(
                                                () ->
                                                        new IllegalArgumentException(
                                                                "Unknown modification '"
                                                                        + name
                                                                        + "'")));
    }

    @Override
    protected ObservedPeptide convert(DelimitedTable.Row row) {
        if (row.get("Reverse").equals("+")) {
            throw new SkippedRow("decoy (Reverse)");
        }
        if (row.get("Potential contaminant").equals("+")) {
            throw new SkippedRow("potential contaminant");
        }
        double intensity = intensity(row.get(INTENSITY));
        String sequence = row.get(SEQUENCE).replace("_", "");
        return new ObservedPeptide(notation.parse(sequence), row.first(SAMPLE), intensity);
    }
}
