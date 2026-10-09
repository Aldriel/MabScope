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

package com.bioinfo.mabscope.quant;

import java.util.Objects;

/**
 * The sequence coverage of a chain by the peptides observed in a sample.
 *
 * @param chainName name of the chain
 * @param sample the sample
 * @param length number of residues of the chain
 * @param coveredResidues number of residues found in at least one observed peptide
 */
public record ChainCoverage(String chainName, String sample, int length, int coveredResidues) {

    /** Validates the coverage. */
    public ChainCoverage {
        Objects.requireNonNull(chainName, "chainName");
        Objects.requireNonNull(sample, "sample");
        if (length < 1 || coveredResidues < 0 || coveredResidues > length) {
            throw new IllegalArgumentException(
                    "Invalid coverage " + coveredResidues + "/" + length);
        }
    }

    /** Returns the percentage of the residues covered. */
    public double percent() {
        return 100.0 * coveredResidues / length;
    }
}
