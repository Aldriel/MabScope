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

import com.bioinfo.proteomics.model.Peptide;

import java.util.Objects;

/**
 * A peptide identified and quantified by the MS software, in the common form produced by every
 * {@link MsResultAdapter}.
 *
 * @param peptide the peptide, with the modifications found on it
 * @param sample sample (lot, replicate, stress condition, ...) in which it was measured
 * @param intensity its abundance in the sample (peak area or intensity), strictly positive
 */
public record ObservedPeptide(Peptide peptide, String sample, double intensity) {

    /** Validates the observation. */
    public ObservedPeptide {
        Objects.requireNonNull(peptide, "peptide");
        Objects.requireNonNull(sample, "sample");
        if (sample.isBlank()) {
            throw new IllegalArgumentException("The sample name is missing");
        }
        if (!(intensity > 0) || Double.isInfinite(intensity)) {
            throw new IllegalArgumentException("The intensity must be a positive number");
        }
    }
}
