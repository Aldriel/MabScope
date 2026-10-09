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

import com.bioinfo.proteomics.model.Modification;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads a Skyline custom report with the columns {@code Peptide Modified Sequence}, {@code
 * Replicate Name} and {@code Total Area MS1} (or {@code Total Area}). Modifications are written
 * either as rounded masses, {@code PEPM[+16]TIDE}, or as names, {@code PEPM[Oxidation (M)]TIDE}. A
 * rounded mass is matched to the known modifications within its rounding.
 */
public final class SkylineAdapter extends TableAdapter {

    private static final String SEQUENCE = "Peptide Modified Sequence";
    private static final String REPLICATE = "Replicate Name";
    private static final List<String> AREA = List.of("Total Area MS1", "Total Area");
    private static final Pattern MASS = Pattern.compile("[+-]\\d+(\\.\\d+)?");

    private final BracketNotation notation;

    /**
     * Creates the adapter.
     *
     * @param catalog resolves the modification names and masses
     */
    public SkylineAdapter(ModificationCatalog catalog) {
        super(MsFormat.SKYLINE, catalog, List.of(List.of(SEQUENCE), List.of(REPLICATE), AREA));
        this.notation = new BracketNotation('[', ']', this::resolve);
    }

    @Override
    protected ObservedPeptide convert(DelimitedTable.Row row) {
        double intensity = intensity(row.first(AREA));
        return new ObservedPeptide(
                notation.parse(row.get(SEQUENCE)), row.get(REPLICATE), intensity);
    }

    private Modification resolve(String text) {
        if (MASS.matcher(text).matches()) {
            int dot = text.indexOf('.');
            int decimals = dot < 0 ? 0 : text.length() - dot - 1;
            double rounding = 0.5 * Math.pow(10, -decimals);
            return catalog.byMass(
                    Double.parseDouble(text), rounding + FragPipeAdapter.MASS_TOLERANCE);
        }
        return catalog.byName(text)
                .orElseThrow(
                        () -> new IllegalArgumentException("Unknown modification '" + text + "'"));
    }
}
