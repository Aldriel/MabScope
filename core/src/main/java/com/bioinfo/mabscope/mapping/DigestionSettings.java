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

import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.digest.ProteinDigester;

import java.util.Objects;

/**
 * How the antibody is digested for the peptide map.
 *
 * @param protease enzyme
 * @param missedCleavages maximum number of missed cleavages
 * @param minLength shortest peptide kept, in residues
 * @param maxLength longest peptide kept, in residues
 * @param alkylatedCysteines whether the cysteines were reduced and alkylated with iodoacetamide, as
 *     in most peptide mapping protocols
 */
public record DigestionSettings(
        Protease protease,
        int missedCleavages,
        int minLength,
        int maxLength,
        boolean alkylatedCysteines) {

    /** Validates the settings. */
    public DigestionSettings {
        Objects.requireNonNull(protease, "protease");
        // The digester checks the numeric limits.
        new ProteinDigester(protease, missedCleavages, minLength, maxLength);
    }

    /** Typical tryptic peptide map: up to 1 missed cleavage, 1 to 60 residues, alkylated. */
    public static DigestionSettings trypsinDefaults() {
        return new DigestionSettings(Protease.TRYPSIN, 1, 1, 60, true);
    }

    /** Returns the digester applying these settings. */
    public ProteinDigester digester() {
        return new ProteinDigester(protease, missedCleavages, minLength, maxLength);
    }
}
