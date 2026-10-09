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

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.model.Region;

import java.util.List;
import java.util.Objects;

/**
 * A peptide of the theoretical peptide map, with the liabilities it contains.
 *
 * @param chainName name of the chain the peptide comes from
 * @param sequence plain sequence
 * @param start 1-based position of the first residue in the chain
 * @param end 1-based position of the last residue, inclusive
 * @param missedCleavages number of internal cleavage sites not cleaved
 * @param proForma the peptide in ProForma notation, with its fixed modifications
 * @param monoisotopicMass neutral monoisotopic mass, in Da
 * @param regions regions covered by the peptide, from its N-terminus
 * @param liabilities liabilities located in the peptide
 * @param modifiedForms the peptide carrying each modification it can undergo
 */
public record MappedPeptide(
        String chainName,
        String sequence,
        int start,
        int end,
        int missedCleavages,
        String proForma,
        double monoisotopicMass,
        List<Region> regions,
        List<Liability> liabilities,
        List<ModifiedForm> modifiedForms) {

    /** Validates the peptide and copies its lists, so that it is immutable. */
    public MappedPeptide {
        Objects.requireNonNull(chainName, "chainName");
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(proForma, "proForma");
        regions = List.copyOf(regions);
        liabilities = List.copyOf(liabilities);
        modifiedForms = List.copyOf(modifiedForms);
    }

    /** Tells whether the peptide covers part of a CDR. */
    public boolean coversCdr() {
        return regions.stream().anyMatch(Region::isCdr);
    }
}
