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

import com.bioinfo.mabscope.liability.LiabilityType;

import java.util.Objects;

/**
 * A peptide carrying one modification at a liability site, with the mass to look for in the LC-MS
 * data.
 *
 * @param type liability the modification comes from
 * @param position 1-based position of the modified residue in the chain
 * @param modification name of the modification, such as {@code Deamidated} or {@code G0F}
 * @param proForma the modified peptide in ProForma notation
 * @param monoisotopicMass neutral monoisotopic mass of the modified peptide, in Da
 */
public record ModifiedForm(
        LiabilityType type,
        int position,
        String modification,
        String proForma,
        double monoisotopicMass) {

    /** Validates the form. */
    public ModifiedForm {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(modification, "modification");
        Objects.requireNonNull(proForma, "proForma");
    }
}
