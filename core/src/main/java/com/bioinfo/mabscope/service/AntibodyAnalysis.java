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

package com.bioinfo.mabscope.service;

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.NumberingScheme;

import java.util.List;
import java.util.Objects;

/**
 * The analysis of every chain of an antibody.
 *
 * @param antibody the analyzed antibody
 * @param scheme definition of the CDR boundaries
 * @param chains one analysis per chain, in the order of the antibody
 */
public record AntibodyAnalysis(
        Antibody antibody, NumberingScheme scheme, List<ChainAnalysis> chains) {

    /** Validates the analysis and copies its chains, so that it is immutable. */
    public AntibodyAnalysis {
        Objects.requireNonNull(antibody, "antibody");
        Objects.requireNonNull(scheme, "scheme");
        chains = List.copyOf(chains);
    }

    /** Returns the liabilities of every chain, chain after chain. */
    public List<Liability> liabilities() {
        return chains.stream().flatMap(c -> c.liabilities().stream()).toList();
    }
}
