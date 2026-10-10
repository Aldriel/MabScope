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

package com.bioinfo.mabscope;

import com.bioinfo.mabscope.examples.ExampleAntibodies;

/** Published sequences of approved therapeutic antibodies, used as test references. */
public final class ReferenceAntibodies {

    /** Variable domain of the trastuzumab (Herceptin) heavy chain. */
    public static final String TRASTUZUMAB_VH = ExampleAntibodies.TRASTUZUMAB_VH;

    /** Variable domain of the trastuzumab light chain (kappa). */
    public static final String TRASTUZUMAB_VL = ExampleAntibodies.TRASTUZUMAB_VL;

    /** Constant domains of a human IgG1 heavy chain: CH1, hinge, CH2 and CH3. */
    public static final String IGG1_CONSTANT = ExampleAntibodies.IGG1_CONSTANT;

    /** Constant domain of a human kappa light chain (CL). */
    public static final String KAPPA_CONSTANT = ExampleAntibodies.KAPPA_CONSTANT;

    /** Complete trastuzumab heavy chain. */
    public static final String TRASTUZUMAB_HEAVY = ExampleAntibodies.TRASTUZUMAB_HEAVY;

    /** Complete trastuzumab light chain. */
    public static final String TRASTUZUMAB_LIGHT = ExampleAntibodies.TRASTUZUMAB_LIGHT;

    /** Variable domain of the adalimumab (Humira) heavy chain. */
    public static final String ADALIMUMAB_VH = ExampleAntibodies.ADALIMUMAB_VH;

    /** Variable domain of the adalimumab light chain (kappa). */
    public static final String ADALIMUMAB_VL = ExampleAntibodies.ADALIMUMAB_VL;

    private ReferenceAntibodies() {}
}
