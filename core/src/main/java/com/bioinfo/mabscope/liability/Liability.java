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

package com.bioinfo.mabscope.liability;

import com.bioinfo.mabscope.model.Region;

import java.util.Objects;

/**
 * A site of an antibody chain that can be modified.
 *
 * @param chainName name of the chain
 * @param type kind of modification
 * @param position 1-based position of the modified residue
 * @param motif residues defining the site, starting with the modified one, such as {@code NG}
 * @param region region containing the modified residue
 * @param risk likelihood that the site affects the product, raised when it lies in a CDR
 */
public record Liability(
        String chainName,
        LiabilityType type,
        int position,
        String motif,
        Region region,
        Risk risk) {

    /** Validates the liability. */
    public Liability {
        Objects.requireNonNull(chainName, "chainName");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(motif, "motif");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(risk, "risk");
        if (position < 1) {
            throw new IllegalArgumentException("position must be >= 1");
        }
        if (motif.isEmpty()) {
            throw new IllegalArgumentException("motif must not be empty");
        }
    }

    /** Tells whether the site lies in the antigen-binding site. */
    public boolean inCdr() {
        return region.isCdr();
    }
}
