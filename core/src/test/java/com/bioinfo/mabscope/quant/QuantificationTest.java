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

package com.bioinfo.mabscope.quant;

import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_HEAVY;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_LIGHT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.LiabilityType;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.mapping.PeptideMapper;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.ms.ModificationCatalog;
import com.bioinfo.mabscope.ms.ObservedPeptide;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Quantifies a simulated peptide mapping of two lots of trastuzumab: lot B has more deamidation of
 * the CDR-H2 asparagine N55 and a different Fc glycan profile than the reference lot A.
 */
class QuantificationTest {

    private static final double DELTA = 1e-9;
    private static final PeptideSequenceParser PARSER =
            new PeptideSequenceParser(new ModificationCatalog().registry());
    private static final AntibodyAnalysis TRASTUZUMAB =
            new AntibodyService()
                    .analyze(
                            Antibody.of("Trastuzumab", TRASTUZUMAB_HEAVY, TRASTUZUMAB_LIGHT),
                            NumberingScheme.KABAT);

    private static final List<ObservedPeptide> LOTS =
            List.of(
                    observed("IYPTNGYTR", "Lot A", 900),
                    observed("IYPTN[Deamidated]GYTR", "Lot A", 100),
                    observed("EEQYN[G0F]STYR", "Lot A", 600),
                    observed("EEQYN[G1F]STYR", "Lot A", 300),
                    observed("EEQYNSTYR", "Lot A", 100),
                    observed("SLSLSPG", "Lot A", 950),
                    observed("SLSLSPGK", "Lot A", 50),
                    observed("[Glu->pyro-Glu]-EVQLVESGGGLVQPGGSLR", "Lot A", 20),
                    observed("EVQLVESGGGLVQPGGSLR", "Lot A", 80),
                    observed("AAAAAAAAK", "Lot A", 10),
                    observed("IYPTNGYTR", "Lot B", 700),
                    observed("IYPTN[Deamidated]GYTR", "Lot B", 300),
                    observed("EEQYN[G0F]STYR", "Lot B", 500),
                    observed("EEQYN[+2000]STYR", "Lot B", 100),
                    observed("EEQYN[Deamidated]STYR", "Lot B", 50),
                    observed("WGGDGFYAM[Oxidation]DYWGQGTLVTVSSASTK", "Lot B", 40),
                    observed("WGGDGFYAMDYWGQGTLVTVSSASTK", "Lot B", 60),
                    observed("W[Dioxidation]GGDGFYAMDYWGQGTLVTVSSASTK", "Lot B", 100));

    private final Quantification result = new SiteQuantifier().quantify(TRASTUZUMAB, LOTS);

    private static ObservedPeptide observed(String proForma, String sample, double intensity) {
        return new ObservedPeptide(PARSER.parse(proForma), sample, intensity);
    }

    private Optional<SiteQuantity> site(LiabilityType type, int position, String sample) {
        return result.sites().stream()
                .filter(q -> q.site().chainName().equals("Heavy chain"))
                .filter(q -> q.site().type() == type && q.site().position() == position)
                .filter(q -> q.sample().equals(sample))
                .findFirst();
    }

    private static int glycosite() {
        return TRASTUZUMAB_HEAVY.indexOf("EEQYNSTYR") + 5;
    }

    @Nested
    class Sites {

        @Test
        void deamidationOfCdrH2() {
            SiteQuantity a = site(LiabilityType.DEAMIDATION, 55, "Lot A").orElseThrow();
            assertEquals(1000, a.totalIntensity(), DELTA);
            assertEquals(10, a.percent("Deamidated"), DELTA);
            assertEquals(
                    30,
                    site(LiabilityType.DEAMIDATION, 55, "Lot B")
                            .orElseThrow()
                            .percent("Deamidated"),
                    DELTA);
            assertTrue(a.site().inCdr());
        }

        @Test
        void glycanProfile() {
            assertEquals(300, glycosite(), "Fc glycosylation site of IgG1");
            SiteQuantity a = site(LiabilityType.N_GLYCOSYLATION, 300, "Lot A").orElseThrow();
            assertEquals(List.of("G0F", "G1F"), List.copyOf(a.forms().keySet()));
            assertEquals(60, a.percent("G0F"), DELTA);
            assertEquals(30, a.percent("G1F"), DELTA);
            assertEquals(90, a.modifiedPercent(), DELTA, "10 % aglycosylated");
            SiteQuantity b = site(LiabilityType.N_GLYCOSYLATION, 300, "Lot B").orElseThrow();
            assertEquals(650, b.totalIntensity(), DELTA);
            assertEquals(100 * 100 / 650.0, b.percent("+2000.000000"), DELTA, "unknown glycan");
            assertEquals(0, b.percent("G1F"), DELTA);
        }

        @Test
        void deamidationOfTheGlycosite() {
            assertEquals(
                    0,
                    site(LiabilityType.DEAMIDATION, 300, "Lot A")
                            .orElseThrow()
                            .percent("Deamidated"),
                    DELTA,
                    "a glycan is not a deamidation");
            assertEquals(
                    100 * 50 / 650.0,
                    site(LiabilityType.DEAMIDATION, 300, "Lot B")
                            .orElseThrow()
                            .percent("Deamidated"),
                    DELTA);
        }

        @Test
        void cTerminalLysine() {
            int last = TRASTUZUMAB_HEAVY.length();
            SiteQuantity a = site(LiabilityType.C_TERMINAL_LYSINE, last, "Lot A").orElseThrow();
            assertEquals(1000, a.totalIntensity(), DELTA);
            assertEquals(95, a.percent(PeptideMapper.LYSINE_LOSS), DELTA);
            assertTrue(site(LiabilityType.C_TERMINAL_LYSINE, last, "Lot B").isEmpty());
        }

        @Test
        void pyroglutamateOnTheNTerminus() {
            assertEquals(
                    20,
                    site(LiabilityType.PYROGLUTAMATE, 1, "Lot A")
                            .orElseThrow()
                            .percent("Glu->pyro-Glu"),
                    DELTA);
        }

        @Test
        void oxidationOfCdrH3() {
            assertEquals(
                    20,
                    site(LiabilityType.OXIDATION, 107, "Lot B").orElseThrow().percent("Oxidation"),
                    DELTA);
            SiteQuantity w99 = site(LiabilityType.OXIDATION, 99, "Lot B").orElseThrow();
            assertEquals(50, w99.percent("Dioxidation"), DELTA);
            assertEquals(0, w99.percent("Oxidation"), DELTA);
            assertTrue(site(LiabilityType.OXIDATION, 107, "Lot A").isEmpty(), "not covered");
        }

        @Test
        void isomerizationIsNotQuantified() {
            assertTrue(
                    result.sites().stream()
                            .noneMatch(q -> q.site().type() == LiabilityType.ISOMERIZATION));
        }

        @Test
        void sitesFollowTheLiabilityOrder() {
            List<Liability> order = TRASTUZUMAB.liabilities();
            for (int i = 1; i < result.sites().size(); i++) {
                assertTrue(
                        order.indexOf(result.sites().get(i - 1).site())
                                <= order.indexOf(result.sites().get(i).site()));
            }
        }
    }

    @Nested
    class CoverageAndSamples {

        @Test
        void samplesInOrder() {
            assertEquals(List.of("Lot A", "Lot B"), result.samples());
        }

        @Test
        void coverageOfEachChain() {
            assertEquals(4, result.coverage().size());
            ChainCoverage heavyA = result.coverage().getFirst();
            assertEquals("Heavy chain", heavyA.chainName());
            assertEquals("Lot A", heavyA.sample());
            assertEquals(450, heavyA.length());
            assertEquals(9 + 9 + 8 + 19, heavyA.coveredResidues());
            assertEquals(100.0 * 45 / 450, heavyA.percent(), DELTA);
            ChainCoverage lightA = result.coverage().get(1);
            assertEquals("Light chain", lightA.chainName());
            assertEquals(0, lightA.coveredResidues());
        }

        @Test
        void unmatchedSequences() {
            assertEquals(List.of("AAAAAAAAK"), result.unmatchedSequences());
        }

        @Test
        void nothingObserved() {
            Quantification empty = new SiteQuantifier().quantify(TRASTUZUMAB, List.of());
            assertTrue(empty.samples().isEmpty());
            assertTrue(empty.coverage().isEmpty());
            assertTrue(empty.sites().isEmpty());
        }

        @Test
        void validation() {
            SiteQuantifier q = new SiteQuantifier();
            assertThrows(NullPointerException.class, () -> q.quantify(null, List.of()));
            assertThrows(NullPointerException.class, () -> q.quantify(TRASTUZUMAB, null));
        }
    }

    @Nested
    class LotComparison {

        private final List<SiteComparison> rows = new LotComparator().compare(result, "Lot A");

        private SiteComparison row(LiabilityType type, int position, String form) {
            return rows.stream()
                    .filter(r -> r.site().type() == type && r.site().position() == position)
                    .filter(r -> r.form().equals(form))
                    .findFirst()
                    .orElseThrow();
        }

        @Test
        void deamidationIncreaseIsFlagged() {
            SiteComparison r = row(LiabilityType.DEAMIDATION, 55, "Deamidated");
            assertEquals(Map.of("Lot A", 10.0, "Lot B", 30.0), r.percents());
            assertEquals(10, r.referencePercent(), DELTA);
            assertEquals(20, r.maxDeviation(), DELTA);
            assertTrue(r.flagged());
        }

        @Test
        void glycoformsOfEveryLot() {
            List<String> forms =
                    rows.stream()
                            .filter(r -> r.site().type() == LiabilityType.N_GLYCOSYLATION)
                            .map(SiteComparison::form)
                            .toList();
            assertEquals(List.of("G0F", "G1F", "+2000.000000"), forms);
            SiteComparison g1f = row(LiabilityType.N_GLYCOSYLATION, 300, "G1F");
            assertEquals(0, g1f.percents().get("Lot B"), DELTA);
            assertTrue(g1f.flagged());
        }

        @Test
        void siteOnlyInTheReference() {
            SiteComparison r = row(LiabilityType.PYROGLUTAMATE, 1, "Glu->pyro-Glu");
            assertEquals(List.of("Lot A"), List.copyOf(r.percents().keySet()));
            assertEquals(0, r.maxDeviation(), DELTA);
            assertFalse(r.flagged());
        }

        @Test
        void siteMissingFromTheReferenceIsNotCompared() {
            assertTrue(rows.stream().noneMatch(r -> r.site().position() == 107));
        }

        @Test
        void threshold() {
            List<SiteComparison> lenient = new LotComparator(25).compare(result, "Lot A");
            assertTrue(
                    lenient.stream()
                            .filter(r -> r.site().position() == 55)
                            .noneMatch(SiteComparison::flagged));
            assertEquals(2.0, LotComparator.DEFAULT_THRESHOLD);
            assertThrows(IllegalArgumentException.class, () -> new LotComparator(-1));
            assertThrows(IllegalArgumentException.class, () -> new LotComparator(Double.NaN));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LotComparator(Double.POSITIVE_INFINITY));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LotComparator().compare(result, "Lot Z"));
        }
    }

    @Nested
    class Records {

        private final Liability site =
                new Liability("H", LiabilityType.PYROGLUTAMATE, 1, "Q", Region.FR1, Risk.MEDIUM);

        @Test
        void expectedForms() {
            assertEquals(Optional.of("Gln->pyro-Glu"), SiteQuantifier.expectedForm(site));
            Liability isoAsp =
                    new Liability(
                            "H", LiabilityType.ISOMERIZATION, 5, "DG", Region.CDR2, Risk.HIGH);
            assertTrue(SiteQuantifier.expectedForm(isoAsp).isEmpty());
        }

        @Test
        void siteQuantity() {
            SiteQuantity q = new SiteQuantity(site, "A", 200, Map.of("Gln->pyro-Glu", 50.0));
            assertEquals(25, q.percent("Gln->pyro-Glu"), DELTA);
            assertEquals(0, q.percent("other"), DELTA);
            assertEquals(25, q.modifiedPercent(), DELTA);
            assertThrows(
                    NullPointerException.class, () -> new SiteQuantity(null, "A", 1, Map.of()));
            assertThrows(
                    NullPointerException.class, () -> new SiteQuantity(site, null, 1, Map.of()));
            assertThrows(
                    IllegalArgumentException.class, () -> new SiteQuantity(site, "A", 0, Map.of()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SiteQuantity(site, "A", Double.NaN, Map.of()));
        }

        @Test
        void chainCoverage() {
            assertThrows(NullPointerException.class, () -> new ChainCoverage(null, "A", 1, 0));
            assertThrows(NullPointerException.class, () -> new ChainCoverage("H", null, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> new ChainCoverage("H", "A", 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new ChainCoverage("H", "A", 5, -1));
            assertThrows(IllegalArgumentException.class, () -> new ChainCoverage("H", "A", 5, 6));
        }

        @Test
        void siteComparison() {
            assertThrows(
                    NullPointerException.class,
                    () -> new SiteComparison(null, "F", Map.of(), 0, 0, false));
            assertThrows(
                    NullPointerException.class,
                    () -> new SiteComparison(site, null, Map.of(), 0, 0, false));
        }
    }
}
