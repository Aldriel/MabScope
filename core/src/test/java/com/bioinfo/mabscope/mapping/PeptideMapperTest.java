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

package com.bioinfo.mabscope.mapping;

import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_HEAVY;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_LIGHT;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.liability.LiabilityType;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.mass.PeptideMassCalculator;
import com.bioinfo.proteomics.model.MassType;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

class PeptideMapperTest {

    private static final double PROTON = 1.007276;

    private final AntibodyService service = new AntibodyService();
    private final AntibodyAnalysis trastuzumab =
            service.analyze(
                    Antibody.of("Trastuzumab", TRASTUZUMAB_HEAVY, TRASTUZUMAB_LIGHT),
                    NumberingScheme.KABAT);

    private List<MappedPeptide> map(DigestionSettings settings) {
        return service.peptideMap(trastuzumab, settings);
    }

    private static MappedPeptide peptide(List<MappedPeptide> map, String chain, String sequence) {
        return map.stream()
                .filter(p -> p.chainName().equals(chain) && p.sequence().equals(sequence))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No peptide " + sequence));
    }

    private static ModifiedForm form(MappedPeptide p, String modification) {
        return p.modifiedForms().stream()
                .filter(f -> f.modification().equals(modification))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + modification + " form"));
    }

    private static double mz(double neutralMass, int charge) {
        return (neutralMass + charge * PROTON) / charge;
    }

    @Nested
    class TrypticMap {

        // Long enough to keep the 63-residue tryptic peptide of CH1.
        private final List<MappedPeptide> map =
                map(new DigestionSettings(Protease.TRYPSIN, 0, 1, 100, true));

        @Test
        void heavyChainAsn55Peptide() {
            MappedPeptide p = peptide(map, "Heavy chain", "IYPTNGYTR");
            assertEquals(51, p.start());
            assertEquals(59, p.end());
            assertEquals(1083.535, p.monoisotopicMass(), 0.001);
            assertEquals(542.775, mz(p.monoisotopicMass(), 2), 0.001);
            assertEquals(List.of(Region.CDR2), p.regions());
            assertTrue(p.coversCdr());
            ModifiedForm deamidated = form(p, "Deamidated");
            assertEquals(55, deamidated.position());
            assertEquals("IYPTN[Deamidated]GYTR", deamidated.proForma());
            assertEquals(0.984016, deamidated.monoisotopicMass() - p.monoisotopicMass(), 1e-6);
        }

        @Test
        void fcGlycopeptide() {
            MappedPeptide p = peptide(map, "Heavy chain", "EEQYNSTYR");
            assertEquals(1188.505, p.monoisotopicMass(), 0.001);
            assertEquals(
                    Glycan.values().length,
                    p.modifiedForms().stream()
                            .filter(f -> f.type() == LiabilityType.N_GLYCOSYLATION)
                            .count());
            assertEquals(LiabilityType.DEAMIDATION, form(p, "Deamidated").type(), "N-S motif");
            ModifiedForm g0f = form(p, "G0F");
            assertEquals(LiabilityType.N_GLYCOSYLATION, g0f.type());
            assertEquals(878.687, mz(g0f.monoisotopicMass(), 3), 0.001);
            assertEquals(List.of(Region.CONSTANT), p.regions());
            assertFalse(p.coversCdr());
        }

        @Test
        void lightChainAsn30Peptide() {
            MappedPeptide p = peptide(map, "Light chain", "ASQDVNTAVAWYQQKPGK");
            assertEquals(25, p.start());
            assertEquals(List.of(Region.CDR1, Region.FR2), p.regions());
            assertEquals(30, form(p, "Deamidated").position());
        }

        @Test
        void cysteinesAreCarbamidomethylated() {
            MappedPeptide p = peptide(map, "Light chain", "VTITCR");
            assertEquals("VTITC[Carbamidomethyl]R", p.proForma());
            assertEquals(
                    PeptideMassCalculator.of(MassType.MONOISOTOPIC).neutralMass("VTITCR")
                            + 57.021464,
                    p.monoisotopicMass(),
                    1e-6);
        }

        @Test
        void chainEnds() {
            ModifiedForm pyroGlu =
                    form(peptide(map, "Heavy chain", "EVQLVESGGGLVQPGGSLR"), "Glu->pyro-Glu");
            assertEquals("E[Glu->pyro-Glu]VQLVESGGGLVQPGGSLR", pyroGlu.proForma());
            MappedPeptide last = peptide(map, "Heavy chain", "SLSLSPGK");
            ModifiedForm clipped = form(last, PeptideMapper.LYSINE_LOSS);
            assertEquals("SLSLSPG", clipped.proForma());
            assertEquals(
                    PeptideMassCalculator.of(MassType.MONOISOTOPIC).neutralMass("SLSLSPG"),
                    clipped.monoisotopicMass(),
                    1e-6);
        }

        @Test
        void isomerizationHasNoMassShift() {
            MappedPeptide p = peptide(map, "Heavy chain", "WGGDGFYAMDYWGQGTLVTVSSASTK");
            assertTrue(
                    p.liabilities().stream()
                            .anyMatch(l -> l.type() == LiabilityType.ISOMERIZATION));
            assertTrue(
                    p.modifiedForms().stream()
                            .noneMatch(f -> f.type() == LiabilityType.ISOMERIZATION));
            List<Integer> oxidized =
                    p.modifiedForms().stream()
                            .filter(f -> f.type() == LiabilityType.OXIDATION)
                            .map(ModifiedForm::position)
                            .toList();
            assertTrue(oxidized.containsAll(List.of(99, 107)), "W99 and M107 of CDR-H3");
        }

        @Test
        void peptidesCoverEachChainInOrder() {
            for (String chain : List.of("Heavy chain", "Light chain")) {
                List<MappedPeptide> peptides =
                        map.stream().filter(p -> p.chainName().equals(chain)).toList();
                int expectedStart = 1;
                for (MappedPeptide p : peptides) {
                    assertEquals(expectedStart, p.start(), chain);
                    expectedStart = p.end() + 1;
                }
            }
        }
    }

    @Nested
    class Settings {

        @Test
        void withoutAlkylation() {
            MappedPeptide p =
                    peptide(
                            map(new DigestionSettings(Protease.TRYPSIN, 0, 1, 60, false)),
                            "Light chain",
                            "VTITCR");
            assertEquals("VTITCR", p.proForma());
        }

        @Test
        void missedCleavagesAndLengthLimits() {
            List<MappedPeptide> map = map(new DigestionSettings(Protease.TRYPSIN, 1, 7, 15, true));
            assertTrue(map.stream().allMatch(p -> p.sequence().length() >= 7));
            assertTrue(map.stream().allMatch(p -> p.sequence().length() <= 15));
            assertTrue(map.stream().anyMatch(p -> p.missedCleavages() == 1));
        }

        @Test
        void otherEnzyme() {
            List<MappedPeptide> map = map(new DigestionSettings(Protease.ASP_N, 0, 1, 500, true));
            assertTrue(
                    map.stream()
                            .filter(p -> p.start() > 1)
                            .allMatch(p -> p.sequence().startsWith("D")));
        }

        @Test
        void defaults() {
            DigestionSettings d = DigestionSettings.trypsinDefaults();
            assertEquals(Protease.TRYPSIN, d.protease());
            assertEquals(1, d.missedCleavages());
            assertTrue(d.alkylatedCysteines());
            assertEquals(Protease.TRYPSIN, d.digester().protease());
        }

        @Test
        void validation() {
            assertThrows(
                    NullPointerException.class, () -> new DigestionSettings(null, 0, 1, 5, true));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DigestionSettings(Protease.TRYPSIN, -1, 1, 5, true));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DigestionSettings(Protease.TRYPSIN, 0, 6, 5, true));
            assertThrows(NullPointerException.class, () -> new PeptideMapper(null));
            PeptideMapper mapper = new PeptideMapper(DigestionSettings.trypsinDefaults());
            assertThrows(NullPointerException.class, () -> mapper.map(null, List.of()));
            assertThrows(
                    NullPointerException.class,
                    () -> mapper.map(trastuzumab.chains().getFirst().annotation(), null));
        }
    }

    @Nested
    class Glycans {

        @Test
        void massesFromComposition() {
            assertEquals(1444.533870, Glycan.G0F.modification().monoisotopicDelta(), 1e-5);
            assertEquals(
                    162.052824,
                    Glycan.G1F.modification().monoisotopicDelta()
                            - Glycan.G0F.modification().monoisotopicDelta(),
                    1e-6,
                    "one galactose");
            assertEquals(
                    146.057909,
                    Glycan.G0F.modification().monoisotopicDelta()
                            - Glycan.G0.modification().monoisotopicDelta(),
                    1e-6,
                    "the core fucose");
            assertTrue(
                    Glycan.G0F.modification().averageDelta()
                            > Glycan.G0F.modification().monoisotopicDelta());
        }

        @Test
        void compositions() {
            assertEquals("HexNAc(4)Hex(3)Fuc(1)", Glycan.G0F.composition());
            assertEquals("HexNAc(2)Hex(5)", Glycan.MAN5.composition());
            assertEquals("G2F", Glycan.G2F.modification().name());
        }
    }

    @Nested
    class Records {

        @Test
        void validation() {
            assertThrows(NullPointerException.class, () -> new ModifiedForm(null, 1, "Ox", "M", 1));
            assertThrows(
                    NullPointerException.class,
                    () -> new ModifiedForm(LiabilityType.OXIDATION, 1, null, "M", 1));
            assertThrows(
                    NullPointerException.class,
                    () -> new ModifiedForm(LiabilityType.OXIDATION, 1, "Ox", null, 1));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new MappedPeptide(
                                    null, "A", 1, 1, 0, "A", 1, List.of(), List.of(), List.of()));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new MappedPeptide(
                                    "H", null, 1, 1, 0, "A", 1, List.of(), List.of(), List.of()));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            new MappedPeptide(
                                    "H", "A", 1, 1, 0, null, 1, List.of(), List.of(), List.of()));
        }
    }
}
