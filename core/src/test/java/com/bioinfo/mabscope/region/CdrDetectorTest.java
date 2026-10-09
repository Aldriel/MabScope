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

package com.bioinfo.mabscope.region;

import static com.bioinfo.mabscope.ReferenceAntibodies.ADALIMUMAB_VH;
import static com.bioinfo.mabscope.ReferenceAntibodies.ADALIMUMAB_VL;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_HEAVY;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_LIGHT;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_VH;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_VL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainAnnotation;
import com.bioinfo.mabscope.model.ChainType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.Region;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CdrDetectorTest {

    private final CdrDetector detector = new CdrDetector();

    private ChainAnnotation heavy(String sequence, NumberingScheme scheme) {
        return detector.annotate(new Chain("H", ChainType.HEAVY, sequence), scheme);
    }

    private ChainAnnotation light(String sequence, NumberingScheme scheme) {
        return detector.annotate(new Chain("L", ChainType.LIGHT, sequence), scheme);
    }

    private static String region(ChainAnnotation annotation, Region region) {
        return annotation.sequence(region).orElseThrow();
    }

    /** CDRs published for trastuzumab and adalimumab (Kabat definition). */
    @Nested
    class PublishedKabatCdrs {

        @Test
        void trastuzumabHeavyChain() {
            ChainAnnotation a = heavy(TRASTUZUMAB_HEAVY, NumberingScheme.KABAT);
            assertEquals("DTYIH", region(a, Region.CDR1));
            assertEquals("RIYPTNGYTRYADSVKG", region(a, Region.CDR2));
            assertEquals("WGGDGFYAMDY", region(a, Region.CDR3));
        }

        @Test
        void trastuzumabLightChain() {
            ChainAnnotation a = light(TRASTUZUMAB_LIGHT, NumberingScheme.KABAT);
            assertEquals("RASQDVNTAVA", region(a, Region.CDR1));
            assertEquals("SASFLYS", region(a, Region.CDR2));
            assertEquals("QQHYTTPPT", region(a, Region.CDR3));
        }

        @Test
        void adalimumabHeavyChain() {
            ChainAnnotation a = heavy(ADALIMUMAB_VH, NumberingScheme.KABAT);
            assertEquals("DYAMH", region(a, Region.CDR1));
            assertEquals("AITWNSGHIDYADSVEG", region(a, Region.CDR2));
            assertEquals("VSYLSTASSLDY", region(a, Region.CDR3));
        }

        @Test
        void adalimumabLightChain() {
            ChainAnnotation a = light(ADALIMUMAB_VL, NumberingScheme.KABAT);
            assertEquals("RASQGIRNYLA", region(a, Region.CDR1));
            assertEquals("AASTLQS", region(a, Region.CDR2));
            assertEquals("QRYNRAPYT", region(a, Region.CDR3));
        }

        @Test
        void kabatPositionsOfTrastuzumabHeavyChain() {
            // Without insertions before CDR-H2, sequential positions equal Kabat numbers.
            ChainAnnotation a = heavy(TRASTUZUMAB_VH, NumberingScheme.KABAT);
            assertEquals(31, a.span(Region.CDR1).orElseThrow().start());
            assertEquals(35, a.span(Region.CDR1).orElseThrow().end());
            assertEquals(50, a.span(Region.CDR2).orElseThrow().start());
        }
    }

    @Nested
    class Schemes {

        @Test
        void chothiaHeavyChain() {
            ChainAnnotation a = heavy(TRASTUZUMAB_VH, NumberingScheme.CHOTHIA);
            assertEquals("GFNIKDT", region(a, Region.CDR1));
            assertEquals("YPTNGY", region(a, Region.CDR2));
            assertEquals("WGGDGFYAMDY", region(a, Region.CDR3));
        }

        @Test
        void abmHeavyChain() {
            ChainAnnotation a = heavy(TRASTUZUMAB_VH, NumberingScheme.ABM);
            assertEquals("GFNIKDTYIH", region(a, Region.CDR1));
            assertEquals("RIYPTNGYTR", region(a, Region.CDR2));
            assertEquals("WGGDGFYAMDY", region(a, Region.CDR3));
        }

        @ParameterizedTest
        @EnumSource(NumberingScheme.class)
        void lightChainCdrsAreTheSameInEveryScheme(NumberingScheme scheme) {
            ChainAnnotation a = light(TRASTUZUMAB_VL, scheme);
            assertSame(scheme, a.scheme());
            assertEquals("RASQDVNTAVA", region(a, Region.CDR1));
            assertEquals("SASFLYS", region(a, Region.CDR2));
            assertEquals("QQHYTTPPT", region(a, Region.CDR3));
        }
    }

    @Nested
    class FrameworkAndConstantRegions {

        @Test
        void completeHeavyChain() {
            ChainAnnotation a = heavy(TRASTUZUMAB_HEAVY, NumberingScheme.KABAT);
            assertEquals(450, a.chain().length(), "an IgG1 heavy chain has 450 residues");
            assertEquals("EVQLVESGGGLVQPGGSLRLSCAASGFNIK", region(a, Region.FR1));
            assertEquals("WVRQAPGKGLEWVA", region(a, Region.FR2));
            assertEquals("WGQGTLVTVSS", region(a, Region.FR4));
            assertTrue(region(a, Region.CONSTANT).startsWith("ASTKGPSVFPLAP"));
            assertEquals(
                    TRASTUZUMAB_VH.length() + 1, a.span(Region.CONSTANT).orElseThrow().start());
        }

        @Test
        void completeLightChain() {
            ChainAnnotation a = light(TRASTUZUMAB_LIGHT, NumberingScheme.KABAT);
            assertEquals(214, a.chain().length(), "a kappa light chain has 214 residues");
            assertEquals("FGQGTKVEIK", region(a, Region.FR4));
            assertEquals(108, a.span(Region.CONSTANT).orElseThrow().start());
            assertTrue(region(a, Region.CONSTANT).startsWith("RTVAAPS"));
        }

        @Test
        void variableDomainAloneHasNoConstantRegion() {
            ChainAnnotation a = light(TRASTUZUMAB_VL, NumberingScheme.KABAT);
            assertTrue(a.span(Region.CONSTANT).isEmpty());
            assertEquals(TRASTUZUMAB_VL.length(), a.span(Region.FR4).orElseThrow().end());
        }

        @Test
        void truncatedFramework4() {
            String cut = TRASTUZUMAB_VH.substring(0, TRASTUZUMAB_VH.length() - 3);
            ChainAnnotation a = heavy(cut, NumberingScheme.KABAT);
            assertEquals("WGQGTLVT", region(a, Region.FR4));
        }

        @Test
        void residuesAreAssignedToTheirRegion() {
            ChainAnnotation a = heavy(TRASTUZUMAB_HEAVY, NumberingScheme.KABAT);
            assertEquals(Region.FR1, a.regionAt(1));
            assertEquals(Region.CDR1, a.regionAt(31));
            assertEquals(Region.CONSTANT, a.regionAt(a.chain().length()));
        }
    }

    @Nested
    class TolerantRules {

        @Test
        void unusualResiduesAfterCdrL1() {
            // W-H-Q instead of W-Y-Q: the tryptophan alone still ends CDR-L1.
            String mutated = TRASTUZUMAB_VL.replace("WYQQKP", "WHQQKP");
            assertEquals("RASQDVNTAVA", region(light(mutated, NumberingScheme.KABAT), Region.CDR1));
        }

        @Test
        void unusualResidueAfterCdrH1() {
            // W-L instead of W-V after CDR-H1.
            String mutated = TRASTUZUMAB_VH.replace("IHWVRQ", "IHWLRQ");
            assertEquals("DTYIH", region(heavy(mutated, NumberingScheme.KABAT), Region.CDR1));
        }

        @Test
        void shiftedFramework3Cysteine() {
            // One residue removed from FR3 of the light chain moves its cysteine by one position.
            String shorter = TRASTUZUMAB_VL.replace("SGSRSGTDF", "SGSRSTDF");
            assertEquals("QQHYTTPPT", region(light(shorter, NumberingScheme.KABAT), Region.CDR3));
        }
    }

    @Nested
    class Failures {

        @Test
        void lightChainAnnotatedAsHeavy() {
            RegionNotFoundException e =
                    assertThrows(
                            RegionNotFoundException.class,
                            () -> heavy(TRASTUZUMAB_VL, NumberingScheme.KABAT));
            assertEquals("H", e.chainName());
        }

        @Test
        void notAnAntibody() {
            String hemoglobin = "MVLSPADKTNVKAAWGKVGAHAGEYGAEALERMFLSF";
            RegionNotFoundException e =
                    assertThrows(
                            RegionNotFoundException.class,
                            () -> light(hemoglobin, NumberingScheme.KABAT));
            assertEquals(Region.CDR1, e.region());
            assertTrue(e.getMessage().contains("no cysteine"), e.getMessage());
        }

        @Test
        void chainTooShortForCdrL1() {
            assertThrows(
                    RegionNotFoundException.class,
                    () -> light(TRASTUZUMAB_VL.substring(0, 30), NumberingScheme.KABAT));
        }

        @Test
        void chainEndsBeforeCdrL2() {
            RegionNotFoundException e =
                    assertThrows(
                            RegionNotFoundException.class,
                            () -> light(TRASTUZUMAB_VL.substring(0, 52), NumberingScheme.KABAT));
            assertEquals(Region.CDR2, e.region());
            assertTrue(e.getMessage().contains("ends at residue 52"), e.getMessage());
        }

        @Test
        void missingMotifAfterCdrH2() {
            String mutated = TRASTUZUMAB_VH.replace("SVKGRFTISA", "SVKGDDDDSA");
            RegionNotFoundException e =
                    assertThrows(
                            RegionNotFoundException.class,
                            () -> heavy(mutated, NumberingScheme.KABAT));
            assertEquals(Region.CDR2, e.region());
        }

        @Test
        void missingCdrH3Cysteine() {
            String mutated = TRASTUZUMAB_VH.replace("YYCSR", "YYASR");
            RegionNotFoundException e =
                    assertThrows(
                            RegionNotFoundException.class,
                            () -> heavy(mutated, NumberingScheme.KABAT));
            assertEquals(Region.CDR3, e.region());
        }

        @Test
        void nullArguments() {
            Chain chain = new Chain("L", ChainType.LIGHT, TRASTUZUMAB_VL);
            assertThrows(
                    NullPointerException.class,
                    () -> detector.annotate(null, NumberingScheme.KABAT));
            assertThrows(NullPointerException.class, () -> detector.annotate(chain, null));
        }
    }
}
