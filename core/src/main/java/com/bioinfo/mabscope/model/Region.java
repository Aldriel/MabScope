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

package com.bioinfo.mabscope.model;

/**
 * Structural regions of an antibody chain, from the N-terminus. The variable domain alternates
 * four framework regions (FR) and three complementarity-determining regions (CDR), which form the
 * antigen-binding site; the constant domains follow.
 */
public enum Region {
    /** Framework region 1. */
    FR1,
    /** Complementarity-determining region 1. */
    CDR1,
    /** Framework region 2. */
    FR2,
    /** Complementarity-determining region 2. */
    CDR2,
    /** Framework region 3. */
    FR3,
    /** Complementarity-determining region 3, the most variable one. */
    CDR3,
    /** Framework region 4, ending the variable domain. */
    FR4,
    /** Constant domains: CL for a light chain; CH1, hinge, CH2 and CH3 for a heavy chain. */
    CONSTANT;

    /** Tells whether the region belongs to the antigen-binding site. */
    public boolean isCdr() {
        return this == CDR1 || this == CDR2 || this == CDR3;
    }
}
