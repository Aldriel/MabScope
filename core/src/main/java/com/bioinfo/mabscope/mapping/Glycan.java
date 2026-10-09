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

package com.bioinfo.mabscope.mapping;

import com.bioinfo.proteomics.model.Modification;

/**
 * The N-glycans most often found on the Fc of antibodies produced in CHO cells, in the Oxford
 * notation. Their masses are computed from their monosaccharide composition.
 */
public enum Glycan {
    /** Core-fucosylated biantennary glycan without galactose, the most abundant one. */
    G0F(4, 3, 1),
    /** G0F with one galactose. */
    G1F(4, 4, 1),
    /** G0F with two galactoses. */
    G2F(4, 5, 1),
    /** G0F without its core fucose; afucosylation increases ADCC. */
    G0(4, 3, 0),
    /** High-mannose glycan, which shortens the half-life of the antibody. */
    MAN5(2, 5, 0);

    // Masses of the monosaccharide residues (Unimod), in Da: monoisotopic, then average.
    private static final double HEXNAC_MONO = 203.079373;
    private static final double HEXNAC_AVG = 203.1925;
    private static final double HEX_MONO = 162.052824;
    private static final double HEX_AVG = 162.1406;
    private static final double FUCOSE_MONO = 146.057909;
    private static final double FUCOSE_AVG = 146.1412;

    private final int hexNAc;
    private final int hexoses;
    private final int fucoses;

    Glycan(int hexNAc, int hexoses, int fucoses) {
        this.hexNAc = hexNAc;
        this.hexoses = hexoses;
        this.fucoses = fucoses;
    }

    /** Returns the composition, such as {@code HexNAc(4)Hex(3)Fuc(1)}. */
    public String composition() {
        String base = "HexNAc(" + hexNAc + ")Hex(" + hexoses + ")";
        return fucoses == 0 ? base : base + "Fuc(" + fucoses + ")";
    }

    /** Returns the glycan as a modification of its asparagine. */
    public Modification modification() {
        double mono = hexNAc * HEXNAC_MONO + hexoses * HEX_MONO + fucoses * FUCOSE_MONO;
        double average = hexNAc * HEXNAC_AVG + hexoses * HEX_AVG + fucoses * FUCOSE_AVG;
        return new Modification(name(), 0, mono, average);
    }
}
