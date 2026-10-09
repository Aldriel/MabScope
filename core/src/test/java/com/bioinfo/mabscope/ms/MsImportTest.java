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

package com.bioinfo.mabscope.ms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.mapping.Glycan;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.parser.ParseIssue;
import com.bioinfo.proteomics.parser.ParsingException;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

class MsImportTest {

    private final MsResultImporter importer = new MsResultImporter();

    private MsImport read(String text) throws IOException {
        return importer.read(new StringReader(text));
    }

    private static List<String> errors(MsImport result) {
        return result.issues().stream()
                .filter(i -> i.severity() == ParseIssue.Severity.ERROR)
                .map(ParseIssue::toString)
                .toList();
    }

    private static List<String> warnings(MsImport result) {
        return result.issues().stream()
                .filter(i -> i.severity() == ParseIssue.Severity.WARNING)
                .map(ParseIssue::message)
                .toList();
    }

    /** Name of the modification of the asparagine of EEQYNSTYR. */
    private static String glycan(ObservedPeptide p) {
        return p.peptide().residues().get(4).modifications().getFirst().name();
    }

    @Nested
    class MaxQuant {

        private static final String EVIDENCE =
                """
Sequence\tModified sequence\tRaw file\tExperiment\tIntensity\tReverse\tPotential contaminant
IYPTNGYTR\t_IYPTN(Deamidation (NQ))GYTR_\tlotA_01\tLot A\t1.5E7\t\t
EVQLVESGGGLVQPGGSLR\t_(Glu->pyro-Glu)EVQLVESGGGLVQPGGSLR_\tlotB_01\tLot B\t2000\t\t
WGGDGFYAMDYWGQGTLVTVSSASTK\t_WGGDGFYAM(ox)DYWGQGTLVTVSSASTK_\tlotA_01\tLot A\t300\t\t
KPEPTIDE\t_KPEPTIDE_\tlotA_01\tLot A\t500\t+\t
ALBUMIN\t_ALBK_\tlotA_01\tLot A\t500\t\t+
IYPTNGYTR\t_IYPTNGYTR_\tlotA_01\tLot A\tNaN\t\t
IYPTNGYTR\t_IYPTNGYTR_\tlotA_01\tLot A\t\t\t
PEPTIDE\t_PEPT(Foo)IDE_\tlotA_01\tLot A\t100\t\t
""";

        @Test
        void evidenceFile() throws IOException {
            MsImport result = read(EVIDENCE);
            assertEquals(MsFormat.MAXQUANT, result.format());
            assertEquals(3, result.peptides().size());
            ObservedPeptide deamidated = result.peptides().getFirst();
            assertEquals("IYPTN[Deamidated]GYTR", deamidated.peptide().toProForma());
            assertEquals("Lot A", deamidated.sample());
            assertEquals(1.5e7, deamidated.intensity());
            assertEquals(
                    "[Glu->pyro-Glu]-EVQLVESGGGLVQPGGSLR",
                    result.peptides().get(1).peptide().toProForma());
            assertEquals(
                    "WGGDGFYAM[Oxidation]DYWGQGTLVTVSSASTK",
                    result.peptides().get(2).peptide().toProForma());
            assertEquals(List.of("Lot A", "Lot B"), result.samples());
            assertEquals(
                    List.of(
                            "1 row(s) skipped: decoy (Reverse)",
                            "1 row(s) skipped: potential contaminant",
                            "2 row(s) skipped: no intensity"),
                    warnings(result));
            assertEquals(List.of("ERROR line 9: Unknown modification 'Foo'"), errors(result));
        }

        @Test
        void rawFileWhenNoExperiment() throws IOException {
            MsImport result =
                    read(
                            "Modified sequence\tRaw file\tIntensity\n"
                                    + "_(Acetyl (Protein N-term))ADK_\tlotA_01\t10\n");
            ObservedPeptide p = result.peptides().getFirst();
            assertEquals("lotA_01", p.sample());
            assertEquals("[Acetyl]-ADK", p.peptide().toProForma());
        }

        @Test
        void malformedSequences() throws IOException {
            MsImport result =
                    read(
                            "Modified sequence\tRaw file\tIntensity\n"
                                    + "_PEPM(Oxidation (M)TIDE_\tr\t10\n"
                                    + "_PEP1IDE_\tr\t10\n"
                                    + "_(Acetyl (Protein N-term))_\tr\t10\n");
            assertTrue(result.peptides().isEmpty());
            List<String> errors = errors(result);
            assertEquals(3, errors.size());
            assertTrue(errors.get(0).contains("Unclosed '('"), errors.get(0));
            assertTrue(errors.get(1).contains("Invalid amino acid '1'"), errors.get(1));
            assertTrue(errors.get(2).contains("No residue"), errors.get(2));
        }
    }

    @Nested
    class FragPipe {

        private static final String PSM =
                """
                Spectrum\tSpectrum File\tPeptide\tCharge\tIntensity\tAssigned Modifications
                s1\tC:\\\\run\\\\interact-lot_A.pep.xml\tIYPTNGYTR\t2\t2000000\t5N(0.9840)
                s2\t/data/lot_B.mzML\tEEQYNSTYR\t3\t1000\t5N(1444.5339)
                s3\tlot_C\tvtitcr\t2\t50\t5C(57.0215), N-term(42.0106)
                s4\tlot_C\tIYPTNGYTR\t2\t5\t2Y(123.4567)
                s5\tlot_C\tIYPTNGYTR\t2\t0\t
                s6\tlot_C\tIYPTNGYTR\t2\t5\t3N(0.9840)
                s7\tlot_C\tIYPTNGYTR\t2\t5\tN(0.98)
                s8\tlot_C\tIYPTNGYTR\t2\t5\t20N(0.98)
                s9\tlot_C\tIYPTNGYTR\t2\t5\t0I(0.98)
                """;

        @Test
        void psmFile() throws IOException {
            MsImport result = read(PSM);
            assertEquals(MsFormat.FRAGPIPE, result.format());
            assertEquals(4, result.peptides().size());
            assertEquals("IYPTN[Deamidated]GYTR", result.peptides().get(0).peptide().toProForma());
            assertEquals("lot_A", result.peptides().get(0).sample());
            assertEquals("G0F", glycan(result.peptides().get(1)));
            assertEquals("lot_B", result.peptides().get(1).sample());
            assertEquals(
                    "[Acetyl]-VTITC[Carbamidomethyl]R",
                    result.peptides().get(2).peptide().toProForma());
            Peptide unknown = result.peptides().get(3).peptide();
            assertEquals(
                    123.4567,
                    unknown.residues().get(1).modifications().getFirst().monoisotopicDelta());
            assertEquals(List.of("1 row(s) skipped: no intensity"), warnings(result));
            assertEquals(4, errors(result).size());
            assertTrue(errors(result).getFirst().contains("does not match"));
            assertTrue(errors(result).get(1).contains("Invalid modification"));
        }

        @Test
        void sampleNames() {
            assertEquals("lot_A", FragPipeAdapter.sample("C:\\run\\interact-lot_A.pep.xml"));
            assertEquals("lot_B", FragPipeAdapter.sample("/data/lot_B.mzML"));
            assertEquals("lot_C", FragPipeAdapter.sample("lot_C"));
            assertEquals(".hidden", FragPipeAdapter.sample(".hidden"));
        }
    }

    @Nested
    class Skyline {

        @Test
        void reportWithMassesAndNames() throws IOException {
            MsImport result =
                    read(
                            """
Protein Name,Peptide Modified Sequence,Precursor Charge,Replicate Name,Total Area MS1,Total Area
HC,IYPTN[+1]GYTR,2,Lot A,15000,1
HC,WGGDGFYAM[Oxidation (M)]DYWGQGTLVTVSSASTK,3,Lot A,1200,1
HC,E[-18]VQLVESGGGLVQPGGSLR,2,Lot B,800,1
HC,EEQYN[+1444.5]STYR,3,Lot A,#N/A,1
HC,PEPT[+999]IDE,2,Lot A,10,1
HC,PEPT[Bogus]IDE,2,Lot A,10,1
""");
            assertEquals(MsFormat.SKYLINE, result.format());
            assertEquals(4, result.peptides().size());
            assertEquals("IYPTN[Deamidated]GYTR", result.peptides().get(0).peptide().toProForma());
            assertEquals(15000, result.peptides().get(0).intensity(), "MS1 area preferred");
            assertEquals(
                    "WGGDGFYAM[Oxidation]DYWGQGTLVTVSSASTK",
                    result.peptides().get(1).peptide().toProForma());
            assertEquals(
                    "E[Glu->pyro-Glu]VQLVESGGGLVQPGGSLR",
                    result.peptides().get(2).peptide().toProForma());
            assertEquals("PEPT[+999.0000]IDE", result.peptides().get(3).peptide().toProForma());
            assertEquals(List.of("1 row(s) skipped: no intensity"), warnings(result));
            assertEquals(List.of("ERROR line 7: Unknown modification 'Bogus'"), errors(result));
        }

        @Test
        void totalAreaWhenNoMs1Area() throws IOException {
            MsImport result =
                    read(
                            "Peptide Modified Sequence,Replicate Name,Total Area\n"
                                    + "EEQYN[+1444.534]STYR,Lot A,42\n");
            assertEquals(42, result.peptides().getFirst().intensity());
            assertEquals("G0F", glycan(result.peptides().getFirst()));
        }
    }

    @Nested
    class Generic {

        @Test
        void proFormaTable() throws IOException {
            MsImport result =
                    read(
                            """
                            sequence;sample;intensity
                            EEQYN[G0F]STYR;Lot A;1,5
                            [Glu->pyro-Glu]-EVQLVESGGGLVQPGGSLR;Lot A;2.5e3
                            PEPT[Unknown]IDE;Lot A;10
                            PEPTIDE;Lot A;abc
                            PEPTIDE;Lot A;-5
                            PEPTIDE;Lot A;Infinity
                            PEPTIDE;;10
                            """);
            assertEquals(MsFormat.GENERIC, result.format());
            assertEquals(2, result.peptides().size());
            assertEquals(1.5, result.peptides().getFirst().intensity(), "decimal comma");
            assertEquals(
                    Glycan.G0F.modification().monoisotopicDelta(),
                    result.peptides()
                            .getFirst()
                            .peptide()
                            .residues()
                            .get(4)
                            .modifications()
                            .getFirst()
                            .monoisotopicDelta());
            List<String> errors = errors(result);
            assertEquals(5, errors.size());
            assertTrue(errors.get(0).contains("Unknown modification"), errors.get(0));
            assertTrue(errors.get(1).contains("Invalid intensity 'abc'"), errors.get(1));
            assertTrue(errors.get(2).contains("Invalid intensity '-5'"), errors.get(2));
            assertTrue(errors.get(3).contains("Invalid intensity 'Infinity'"), errors.get(3));
            assertTrue(errors.get(4).contains("sample name is missing"), errors.get(4));
        }
    }

    @Nested
    class Importer {

        @Test
        void unrecognizedOrEmptyFile() {
            ParsingException unknown =
                    assertThrows(ParsingException.class, () -> read("a,b,c\n1,2,3\n"));
            assertEquals("header", unknown.location());
            assertThrows(ParsingException.class, () -> read(""));
        }

        @Test
        void explicitFormat() throws IOException {
            String generic = "sequence,sample,intensity\nPEPTIDE,Lot A,10\n";
            MsImport result = importer.read(new StringReader(generic), MsFormat.GENERIC);
            assertEquals(1, result.peptides().size());
            ParsingException missing =
                    assertThrows(
                            ParsingException.class,
                            () -> importer.read(new StringReader(generic), MsFormat.SKYLINE));
            assertTrue(
                    missing.detail()
                            .contains(
                                    "Peptide Modified Sequence, Replicate Name,"
                                            + " Total Area MS1 or Total Area"),
                    missing.detail());
            assertThrows(
                    NullPointerException.class,
                    () -> importer.read(new StringReader(generic), null));
        }

        @Test
        void detection() {
            assertEquals(
                    MsFormat.MAXQUANT,
                    importer.detect(
                                    DelimitedTable.parse(
                                            "Modified sequence\tRaw file\tIntensity\tsequence"
                                                    + "\tsample"))
                            .orElseThrow(),
                    "the specific format wins over the generic one");
            assertTrue(importer.detect(DelimitedTable.parse("x\n")).isEmpty());
        }

        @Test
        void formats() {
            for (MsFormat f : MsFormat.values()) {
                assertFalse(f.label().isBlank());
            }
            assertThrows(NullPointerException.class, () -> new MaxQuantAdapter(null));
            assertEquals(
                    MsFormat.FRAGPIPE, new FragPipeAdapter(new ModificationCatalog()).format());
        }

        @Test
        void importRecord() {
            assertThrows(
                    NullPointerException.class, () -> new MsImport(null, List.of(), List.of()));
            MsImport empty = new MsImport(MsFormat.GENERIC, List.of(), List.of());
            assertTrue(empty.samples().isEmpty());
        }
    }

    @Nested
    class Catalog {

        private final ModificationCatalog catalog = new ModificationCatalog();

        @Test
        void byName() {
            assertSame(Modification.DEAMIDATED, catalog.byName("Deamidation (NQ)").orElseThrow());
            assertSame(Modification.DEAMIDATED, catalog.byName("Deamidated").orElseThrow());
            assertSame(Modification.OXIDATION, catalog.byName(" ox ").orElseThrow());
            assertSame(
                    Modification.ACETYL, catalog.byName("Acetyl (Protein N-term)").orElseThrow());
            assertSame(Modification.OXIDATION, catalog.byName("UNIMOD:35").orElseThrow());
            assertEquals("G1F", catalog.byName("HexNAc(4)Hex(4)Fuc(1)").orElseThrow().name());
            assertEquals("MAN5", catalog.byName("Man5").orElseThrow().name());
            assertTrue(catalog.byName("Unknown (M)").isEmpty());
            assertTrue(catalog.byName("(M)").isEmpty());
        }

        @Test
        void byMass() {
            assertSame(Modification.OXIDATION, catalog.byMass(15.99, 0.01));
            assertSame(Modification.DIOXIDATION, catalog.byMass(32, 0.5));
            assertEquals("G2F", catalog.byMass(1768.64, 0.01).name());
            Modification unknown = catalog.byMass(10.5, 0.01);
            assertTrue(unknown.isMassOnly());
            assertEquals(10.5, unknown.monoisotopicDelta());
            assertThrows(IllegalArgumentException.class, () -> catalog.byMass(Double.NaN, 0.1));
            assertThrows(IllegalArgumentException.class, () -> catalog.byMass(1, -0.1));
            assertThrows(IllegalArgumentException.class, () -> catalog.byMass(1, Double.NaN));
        }

        @Test
        void registryForProForma() {
            assertTrue(catalog.registry().find("G0F").isPresent());
            assertTrue(catalog.registry().find("Gln->pyro-Glu").isPresent());
        }
    }

    @Nested
    class Observation {

        private final Peptide peptide = Peptide.of("PEPTIDE");

        @Test
        void validation() {
            assertEquals("Lot A", new ObservedPeptide(peptide, "Lot A", 1).sample());
            assertThrows(NullPointerException.class, () -> new ObservedPeptide(null, "A", 1));
            assertThrows(NullPointerException.class, () -> new ObservedPeptide(peptide, null, 1));
            assertThrows(
                    IllegalArgumentException.class, () -> new ObservedPeptide(peptide, " ", 1));
            for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new ObservedPeptide(peptide, "A", bad));
            }
        }
    }
}
