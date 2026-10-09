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
import com.bioinfo.proteomics.model.Peptide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code psm.tsv} file of FragPipe: one row per spectrum match, with the plain peptide
 * and its modifications listed as {@code 5M(15.9949), N-term(42.0106)}. The modifications are
 * recognized by their mass. The sample is the name of the spectrum file, without its folder, its
 * {@code interact-} prefix and its extension.
 */
public final class FragPipeAdapter extends TableAdapter {

    /** Largest gap, in Da, between a reported mass shift and a known modification. */
    static final double MASS_TOLERANCE = 0.01;

    private static final String PEPTIDE = "Peptide";
    private static final String MODIFICATIONS = "Assigned Modifications";
    private static final String FILE = "Spectrum File";
    private static final String INTENSITY = "Intensity";
    private static final Pattern MODIFICATION =
            Pattern.compile("(?:(\\d+)([A-Z])|N-term)\\((-?\\d+(?:\\.\\d+)?)\\)");

    /**
     * Creates the adapter.
     *
     * @param catalog recognizes the modifications by mass
     */
    public FragPipeAdapter(ModificationCatalog catalog) {
        super(
                MsFormat.FRAGPIPE,
                catalog,
                List.of(
                        List.of(PEPTIDE),
                        List.of(MODIFICATIONS),
                        List.of(FILE),
                        List.of(INTENSITY)));
    }

    @Override
    protected ObservedPeptide convert(DelimitedTable.Row row) {
        double intensity = intensity(row.get(INTENSITY));
        Peptide peptide = Peptide.of(row.get(PEPTIDE).toUpperCase(Locale.ROOT));
        String assigned = row.get(MODIFICATIONS);
        if (!assigned.isEmpty()) {
            for (String item : assigned.split(",")) {
                peptide = modify(peptide, item.strip());
            }
        }
        return new ObservedPeptide(peptide, sample(row.get(FILE)), intensity);
    }

    private Peptide modify(Peptide peptide, String item) {
        Matcher m = MODIFICATION.matcher(item);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid modification '" + item + "'");
        }
        Modification mod = catalog.byMass(Double.parseDouble(m.group(3)), MASS_TOLERANCE);
        if (m.group(1) == null) {
            List<Modification> nTerm = new ArrayList<>(peptide.nTermModifications());
            nTerm.add(mod);
            return new Peptide(peptide.residues(), nTerm, peptide.cTermModifications());
        }
        int position = Integer.parseInt(m.group(1));
        if (position < 1
                || position > peptide.length()
                || peptide.sequence().charAt(position - 1) != m.group(2).charAt(0)) {
            throw new IllegalArgumentException(
                    "Modification '" + item + "' does not match " + peptide.sequence());
        }
        return peptide.withModificationAt(position, mod);
    }

    /** Derives the sample name from a path such as {@code C:\run\interact-lot_A.pep.xml}. */
    static String sample(String file) {
        String name = file.substring(Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\')) + 1);
        if (name.startsWith("interact-")) {
            name = name.substring("interact-".length());
        }
        if (name.endsWith(".pep.xml")) {
            name = name.substring(0, name.length() - ".pep.xml".length());
        } else if (name.lastIndexOf('.') > 0) {
            name = name.substring(0, name.lastIndexOf('.'));
        }
        return name;
    }
}
