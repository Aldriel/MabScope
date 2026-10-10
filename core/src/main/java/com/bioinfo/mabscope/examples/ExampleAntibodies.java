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

package com.bioinfo.mabscope.examples;

import com.bioinfo.mabscope.model.Antibody;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Published sequences of approved therapeutic antibodies, offered as examples by the user
 * interfaces, with a simulated peptide mapping dataset for demonstrations.
 */
public final class ExampleAntibodies {

    /** Variable domain of the trastuzumab (Herceptin) heavy chain. */
    public static final String TRASTUZUMAB_VH =
            "EVQLVESGGGLVQPGGSLRLSCAASGFNIKDTYIHWVRQAPGKGLEWVARIYPTNGYTRYADSVKG"
                    + "RFTISADTSKNTAYLQMNSLRAEDTAVYYCSRWGGDGFYAMDYWGQGTLVTVSS";

    /** Variable domain of the trastuzumab light chain (kappa). */
    public static final String TRASTUZUMAB_VL =
            "DIQMTQSPSSLSASVGDRVTITCRASQDVNTAVAWYQQKPGKAPKLLIYSASFLYSGVPSRFSGSRSG"
                    + "TDFTLTISSLQPEDFATYYCQQHYTTPPTFGQGTKVEIK";

    /** Constant domains of a human IgG1 heavy chain: CH1, hinge, CH2 and CH3. */
    public static final String IGG1_CONSTANT =
            "ASTKGPSVFPLAPSSKSTSGGTAALGCLVKDYFPEPVTVSWNSGALTSGVHTFPAVLQSSGLYSLSSVV"
                    + "TVPSSSLGTQTYICNVNHKPSNTKVDKKVEPKSCDKTHTCPPCPAPELLGGPSVFLFPPKPKDTLMI"
                    + "SRTPEVTCVVVDVSHEDPEVKFNWYVDGVEVHNAKTKPREEQYNSTYRVVSVLTVLHQDWLNGKEYK"
                    + "CKVSNKALPAPIEKTISKAKGQPREPQVYTLPPSREEMTKNQVSLTCLVKGFYPSDIAVEWESNGQP"
                    + "ENNYKTTPPVLDSDGSFFLYSKLTVDKSRWQQGNVFSCSVMHEALHNHYTQKSLSLSPGK";

    /** Constant domain of a human kappa light chain (CL). */
    public static final String KAPPA_CONSTANT =
            "RTVAAPSVFIFPPSDEQLKSGTASVVCLLNNFYPREAKVQWKVDNALQSGNSQESVTEQDSKDSTYSL"
                    + "SSTLTLSKADYEKHKVYACEVTHQGLSSPVTKSFNRGEC";

    /** Complete trastuzumab heavy chain. */
    public static final String TRASTUZUMAB_HEAVY = TRASTUZUMAB_VH + IGG1_CONSTANT;

    /** Complete trastuzumab light chain. */
    public static final String TRASTUZUMAB_LIGHT = TRASTUZUMAB_VL + KAPPA_CONSTANT;

    /** Variable domain of the adalimumab (Humira) heavy chain. */
    public static final String ADALIMUMAB_VH =
            "EVQLVESGGGLVQPGRSLRLSCAASGFTFDDYAMHWVRQAPGKGLEWVSAITWNSGHIDYADSVEG"
                    + "RFTISRDNAKNSLYLQMNSLRAEDTAVYYCAKVSYLSTASSLDYWGQGTLVTVSS";

    /** Variable domain of the adalimumab light chain (kappa). */
    public static final String ADALIMUMAB_VL =
            "DIQMTQSPSSLSASVGDRVTITCRASQGIRNYLAWYQQKPGKAPKLLIYAASTLQSGVPSRFSGSGSG"
                    + "TDFTLTISSLQPEDVATYYCQRYNRAPYTFGQGTKVEIK";

    private static final String DEMO_RESULTS = "trastuzumab-lots-demo.csv";

    private ExampleAntibodies() {}

    /** Returns trastuzumab, a humanized IgG1 kappa targeting HER2. */
    public static Antibody trastuzumab() {
        return Antibody.of("Trastuzumab", TRASTUZUMAB_HEAVY, TRASTUZUMAB_LIGHT);
    }

    /** Returns adalimumab, a human IgG1 kappa targeting TNF-alpha. */
    public static Antibody adalimumab() {
        return Antibody.of(
                "Adalimumab", ADALIMUMAB_VH + IGG1_CONSTANT, ADALIMUMAB_VL + KAPPA_CONSTANT);
    }

    /**
     * Returns a simulated peptide mapping of three samples of trastuzumab, in the generic CSV
     * format: the reference lot, a second lot with a different glycan profile, and the reference
     * stressed 4 weeks at 40 °C, with more deamidation and oxidation.
     *
     * @return the content of the CSV file
     */
    public static String demoResults() {
        try (InputStream in = ExampleAntibodies.class.getResourceAsStream(DEMO_RESULTS)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
