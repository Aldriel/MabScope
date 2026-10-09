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

/** The two kinds of polypeptide chains of an antibody. */
public enum ChainType {
    /** Heavy chain: variable domain VH followed by the constant domains (CH1, hinge, CH2, CH3). */
    HEAVY,
    /** Light chain, kappa or lambda: variable domain VL followed by the constant domain CL. */
    LIGHT
}
