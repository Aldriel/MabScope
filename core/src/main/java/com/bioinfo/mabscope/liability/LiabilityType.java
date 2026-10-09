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

/** Chemical or post-translational changes that can make an antibody heterogeneous. */
public enum LiabilityType {
    /** Asparagine turning into aspartate or isoaspartate (+0.984 Da), fastest before G or S. */
    DEAMIDATION,
    /** Aspartate turning into isoaspartate (no mass change), fastest before G or S. */
    ISOMERIZATION,
    /** Oxidation of a methionine or a tryptophan, by light, metals or peroxides. */
    OXIDATION,
    /** N-X-S/T sequon (X not P) that can carry an N-linked glycan. */
    N_GLYCOSYLATION,
    /** Cysteine of the variable domain outside the conserved disulfide bond. */
    UNPAIRED_CYSTEINE,
    /** N-terminal glutamine or glutamate cyclizing into pyroglutamate. */
    PYROGLUTAMATE,
    /** C-terminal lysine of the heavy chain, partly removed by carboxypeptidases. */
    C_TERMINAL_LYSINE,
    /** Asp-Pro bond, cleaved at low pH. */
    ASP_PRO_CLEAVAGE
}
