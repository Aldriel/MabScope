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

import static com.bioinfo.mabscope.ReferenceAntibodies.IGG1_CONSTANT;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_HEAVY;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_LIGHT;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_VH;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_VL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainAnnotation;
import com.bioinfo.mabscope.model.ChainType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.model.RegionSpan;
import com.bioinfo.mabscope.region.CdrDetector;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class LiabilityScannerTest {

    private final LiabilityScanner scanner = LiabilityScanner.withDefaultRules();

    private static ChainAnnotation annotate(ChainType type, String sequence) {
        return new CdrDetector()
                .annotate(new Chain(type.name(), type, sequence), NumberingScheme.KABAT);
    }

    private List<Liability> scan(ChainType type, String sequence) {
        return scanner.scan(annotate(type, sequence));
    }

    private static Liability at(List<Liability> found, LiabilityType type, int position) {
        return found.stream()
                .filter(l -> l.type() == type && l.position() == position)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " at " + position));
    }

    private static List<Liability> ofType(List<Liability> found, LiabilityType type) {
        return found.stream().filter(l -> l.type() == type).toList();
    }

    /** Sites measured in trastuzumab by Harris et al., J Chromatogr B 752 (2001) 233-245. */
    @Nested
    class PublishedTrastuzumabSites {

        @Test
        void lightChainAsn30Deamidation() {
            Liability l =
                    at(scan(ChainType.LIGHT, TRASTUZUMAB_LIGHT), LiabilityType.DEAMIDATION, 30);
            assertEquals("NT", l.motif());
            assertEquals(Region.CDR1, l.region());
            assertEquals(Risk.MEDIUM, l.risk(), "slow NT motif, raised because it is in a CDR");
        }

        @Test
        void heavyChainAsn55Deamidation() {
            Liability l =
                    at(scan(ChainType.HEAVY, TRASTUZUMAB_HEAVY), LiabilityType.DEAMIDATION, 55);
            assertEquals("NG", l.motif());
            assertEquals(Region.CDR2, l.region());
            assertEquals(Risk.HIGH, l.risk());
        }

        @Test
        void heavyChainAsp102Isomerization() {
            Liability l =
                    at(scan(ChainType.HEAVY, TRASTUZUMAB_HEAVY), LiabilityType.ISOMERIZATION, 102);
            assertEquals("DG", l.motif());
            assertEquals(Region.CDR3, l.region());
            assertEquals(Risk.HIGH, l.risk());
            assertTrue(l.inCdr());
        }
    }

    @Nested
    class ConstantRegion {

        private final List<Liability> heavy = scan(ChainType.HEAVY, TRASTUZUMAB_HEAVY);

        @Test
        void fcGlycosylationSiteIsExpected() {
            List<Liability> sequons = ofType(heavy, LiabilityType.N_GLYCOSYLATION);
            assertEquals(1, sequons.size(), "IgG1 has a single sequon, in CH2");
            Liability n297 = sequons.getFirst();
            int expected = TRASTUZUMAB_VH.length() + IGG1_CONSTANT.indexOf("QYNST") + 3;
            assertEquals(expected, n297.position());
            assertEquals("NST", n297.motif());
            assertEquals(Region.CONSTANT, n297.region());
            assertEquals(Risk.LOW, n297.risk());
        }

        @Test
        void fcMethionines() {
            // M252 and M428 (EU numbering), in the FcRn binding site.
            int m252 = TRASTUZUMAB_VH.length() + IGG1_CONSTANT.indexOf("DTLMISR") + 4;
            int m428 = TRASTUZUMAB_VH.length() + IGG1_CONSTANT.indexOf("SVMHEAL") + 3;
            assertEquals(Risk.MEDIUM, at(heavy, LiabilityType.OXIDATION, m252).risk());
            assertEquals(Region.CONSTANT, at(heavy, LiabilityType.OXIDATION, m428).region());
        }

        @Test
        void chainEnds() {
            Liability lysine = at(heavy, LiabilityType.C_TERMINAL_LYSINE, 450);
            assertEquals(Risk.LOW, lysine.risk());
            Liability pyroGlu = at(heavy, LiabilityType.PYROGLUTAMATE, 1);
            assertEquals("E", pyroGlu.motif());
            assertEquals(Risk.LOW, pyroGlu.risk(), "glutamate cyclizes slowly");
        }

        @Test
        void noUnpairedCysteineInANativeAntibody() {
            assertTrue(ofType(heavy, LiabilityType.UNPAIRED_CYSTEINE).isEmpty());
            assertTrue(
                    ofType(
                                    scan(ChainType.LIGHT, TRASTUZUMAB_LIGHT),
                                    LiabilityType.UNPAIRED_CYSTEINE)
                            .isEmpty());
        }

        @Test
        void lightChainHasNoLysineOrPyroglutamateLiability() {
            List<Liability> light = scan(ChainType.LIGHT, TRASTUZUMAB_LIGHT);
            assertTrue(ofType(light, LiabilityType.C_TERMINAL_LYSINE).isEmpty());
            assertTrue(ofType(light, LiabilityType.PYROGLUTAMATE).isEmpty(), "starts with D");
        }
    }

    @Nested
    class EngineeredVariants {

        @Test
        void glycosylationSequonInACdr() {
            // CDR-H3 WGGDGFYAMDY becomes WGGNGTYAMDY: N-G-T is a sequon.
            String mutated = TRASTUZUMAB_VH.replace("WGGDGFYAMDY", "WGGNGTYAMDY");
            Liability l = at(scan(ChainType.HEAVY, mutated), LiabilityType.N_GLYCOSYLATION, 102);
            assertEquals(Region.CDR3, l.region());
            assertEquals(Risk.HIGH, l.risk());
        }

        @Test
        void prolineBlocksGlycosylation() {
            String mutated = TRASTUZUMAB_VH.replace("WGGDGFYAMDY", "WGGNPTYAMDY");
            assertTrue(
                    ofType(scan(ChainType.HEAVY, mutated), LiabilityType.N_GLYCOSYLATION)
                            .isEmpty());
        }

        @Test
        void extraCysteineInACdr() {
            String mutated = TRASTUZUMAB_VH.replace("WGGDGFYAMDY", "WGGDGFYCMDY");
            List<Liability> cysteines =
                    ofType(scan(ChainType.HEAVY, mutated), LiabilityType.UNPAIRED_CYSTEINE);
            assertEquals(1, cysteines.size());
            assertEquals(106, cysteines.getFirst().position());
            assertEquals(Risk.HIGH, cysteines.getFirst().risk());
        }

        @Test
        void glutamineCyclizesToPyroglutamate() {
            String mutated = "Q" + TRASTUZUMAB_VH.substring(1);
            Liability l = at(scan(ChainType.HEAVY, mutated), LiabilityType.PYROGLUTAMATE, 1);
            assertEquals(Risk.MEDIUM, l.risk());
        }

        @Test
        void aspProBondInACdr() {
            String mutated = TRASTUZUMAB_VL.replace("QQHYTTPPT", "QQHYTDPPT");
            Liability l = at(scan(ChainType.LIGHT, mutated), LiabilityType.ASP_PRO_CLEAVAGE, 94);
            assertEquals("DP", l.motif());
            assertEquals(Risk.HIGH, l.risk(), "medium, raised in CDR-L3");
        }

        @Test
        void tryptophanInACdr() {
            Liability w = at(scan(ChainType.HEAVY, TRASTUZUMAB_VH), LiabilityType.OXIDATION, 99);
            assertEquals("W", w.motif());
            assertEquals(Risk.MEDIUM, w.risk(), "low for a tryptophan, raised in CDR-H3");
        }
    }

    @Nested
    class Scanner {

        @Test
        void liabilitiesAreSortedByPositionThenType() {
            List<Liability> found = scan(ChainType.HEAVY, TRASTUZUMAB_HEAVY);
            for (int i = 1; i < found.size(); i++) {
                Liability previous = found.get(i - 1);
                Liability current = found.get(i);
                assertTrue(
                        previous.position() < current.position()
                                || previous.position() == current.position()
                                        && previous.type().compareTo(current.type()) <= 0);
            }
            assertThrows(UnsupportedOperationException.class, () -> found.remove(0));
        }

        @Test
        void customRules() {
            LiabilityScanner onlyDeamidation =
                    new LiabilityScanner(List.of(DipeptideRule.deamidation()));
            List<Liability> found =
                    onlyDeamidation.scan(annotate(ChainType.HEAVY, TRASTUZUMAB_HEAVY));
            assertFalse(found.isEmpty());
            assertTrue(found.stream().allMatch(l -> l.type() == LiabilityType.DEAMIDATION));
            assertTrue(
                    new LiabilityScanner(List.of())
                            .scan(annotate(ChainType.LIGHT, TRASTUZUMAB_VL))
                            .isEmpty());
        }

        @Test
        void unpairedCysteineWithoutConservedCysteines() {
            // A hand-made annotation whose FR1 and FR3 have no cysteine: every cysteine is
            // unpaired.
            Chain chain = new Chain("H", ChainType.HEAVY, "ACAAAAAC");
            ChainAnnotation a =
                    new ChainAnnotation(
                            chain,
                            NumberingScheme.KABAT,
                            List.of(
                                    new RegionSpan(Region.FR1, 1, 1),
                                    new RegionSpan(Region.CDR1, 2, 2),
                                    new RegionSpan(Region.FR2, 3, 3),
                                    new RegionSpan(Region.CDR2, 4, 4),
                                    new RegionSpan(Region.FR3, 5, 5),
                                    new RegionSpan(Region.CDR3, 6, 6),
                                    new RegionSpan(Region.FR4, 7, 7),
                                    new RegionSpan(Region.CONSTANT, 8, 8)));
            List<Liability> found = new UnpairedCysteineRule().find(a);
            assertEquals(1, found.size(), "the constant-region cysteine is ignored");
            assertEquals(2, found.getFirst().position());
        }

        @Test
        void validation() {
            assertThrows(NullPointerException.class, () -> new LiabilityScanner(null));
            assertThrows(NullPointerException.class, () -> scanner.scan(null));
            assertThrows(
                    NullPointerException.class,
                    () -> new DipeptideRule(null, 'N', Map.of('G', Risk.HIGH)));
        }
    }

    @Nested
    class Records {

        @Test
        void riskLevels() {
            assertEquals(Risk.MEDIUM, Risk.LOW.raised());
            assertEquals(Risk.HIGH, Risk.MEDIUM.raised());
            assertEquals(Risk.HIGH, Risk.HIGH.raised());
        }

        @Test
        void liabilityValidation() {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new Liability(
                                    "H", LiabilityType.OXIDATION, 0, "M", Region.FR1, Risk.LOW));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Liability("H", LiabilityType.OXIDATION, 1, "", Region.FR1, Risk.LOW));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new Liability(
                                    null, LiabilityType.OXIDATION, 1, "M", Region.FR1, Risk.LOW));
            assertThrows(
                    NullPointerException.class,
                    () -> new Liability("H", null, 1, "M", Region.FR1, Risk.LOW));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new Liability(
                                    "H", LiabilityType.OXIDATION, 1, null, Region.FR1, Risk.LOW));
            assertThrows(
                    NullPointerException.class,
                    () -> new Liability("H", LiabilityType.OXIDATION, 1, "M", null, Risk.LOW));
            assertThrows(
                    NullPointerException.class,
                    () -> new Liability("H", LiabilityType.OXIDATION, 1, "M", Region.FR1, null));
        }
    }
}
