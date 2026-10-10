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

package com.bioinfo.mabscope.report;

import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_HEAVY;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_LIGHT;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.liability.LiabilityScanner;
import com.bioinfo.mabscope.liability.LiabilityType;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.ms.ObservedPeptide;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

class ReportTest {

    private static final Instant GENERATED = Instant.parse("2026-10-09T18:30:00Z");
    private static final AntibodyService SERVICE = new AntibodyService();
    private static final Antibody TRASTUZUMAB =
            Antibody.of("Trastuzumab", TRASTUZUMAB_HEAVY, TRASTUZUMAB_LIGHT);
    private static final AntibodyAnalysis ANALYSIS =
            SERVICE.analyze(TRASTUZUMAB, NumberingScheme.KABAT);

    private static List<ObservedPeptide> lots() {
        PeptideSequenceParser parser = new PeptideSequenceParser();
        return List.of(
                new ObservedPeptide(parser.parse("IYPTNGYTR"), "Lot A", 900),
                new ObservedPeptide(parser.parse("IYPTN[Deamidated]GYTR"), "Lot A", 100),
                new ObservedPeptide(parser.parse("IYPTNGYTR"), "Lot B", 700),
                new ObservedPeptide(parser.parse("IYPTN[Deamidated]GYTR"), "Lot B", 300),
                new ObservedPeptide(parser.parse("ELVISLIVESK"), "Lot A", 5));
    }

    private static QcReport.Builder complete(Locale locale) {
        DigestionSettings settings = DigestionSettings.trypsinDefaults();
        Quantification q = SERVICE.quantify(ANALYSIS, lots());
        return QcReport.builder(ANALYSIS)
                .locale(locale)
                .zone(ZoneOffset.UTC)
                .generatedAt(GENERATED)
                .appVersion("1.0.0")
                .analyst("Maxime Ethier")
                .notes("Forced degradation, 4 weeks at 40 C.\nSecond paragraph.")
                .peptideMap(settings, SERVICE.peptideMap(ANALYSIS, settings))
                .quantification(q)
                .comparison("Lot A", 2, SERVICE.compareLots(q, "Lot A", 2));
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Nested
    class Writer {

        private final PdfReportWriter writer = new PdfReportWriter();

        @Test
        void completeReportInEnglish() throws IOException {
            byte[] pdf = writer.toBytes(complete(Locale.ENGLISH).build());
            String text = text(pdf);
            for (String expected :
                    List.of(
                            "MabScope",
                            "Antibody quality control report",
                            "Trastuzumab",
                            "Kabat",
                            "Heavy chain (450 residues)",
                            "Maxime Ethier",
                            "Chains and CDRs",
                            "DTYIH",
                            "Liabilities",
                            "Deamidation",
                            "N55",
                            "Theoretical peptide map",
                            "Trypsin, up to 1 missed cleavage(s)",
                            "IYPTNGYTR",
                            "1083.53",
                            "Sequence coverage",
                            "1 observed sequence(s) found in no chain: ELVISLIVESK",
                            "Modification levels",
                            "10.0%",
                            "Lot comparison",
                            "Reference: Lot A",
                            "Lot B: 30.0%",
                            "20.0 pts",
                            "To review",
                            "Notes",
                            "Second paragraph.",
                            "contact@maximeethier.com",
                            "https://www.maximeethier.com/en",
                            "For research use only")) {
                assertTrue(text.contains(expected), "missing: " + expected);
            }
            int count = pages(pdf);
            assertTrue(count > 2, "the peptide map spans several pages");
            assertTrue(text.contains("Page " + count + " of " + count));
        }

        @Test
        void metadata() throws IOException {
            byte[] pdf = writer.toBytes(complete(Locale.ENGLISH).build());
            try (PDDocument document = Loader.loadPDF(pdf)) {
                assertEquals(
                        "Trastuzumab - Antibody quality control report",
                        document.getDocumentInformation().getTitle());
                assertEquals("Maxime Ethier", document.getDocumentInformation().getAuthor());
                assertEquals("MabScope 1.0.0", document.getDocumentInformation().getCreator());
                assertEquals(
                        PDRectangle.A4.getWidth(), document.getPage(0).getMediaBox().getWidth());
            }
        }

        @Test
        void completeReportInFrench() throws IOException {
            String text = text(writer.toBytes(complete(Locale.CANADA_FRENCH).build()));
            for (String expected :
                    List.of(
                            "Rapport de contrôle qualité d'anticorps",
                            "Chaînes et CDR",
                            "règles d'Andrew Martin",
                            "Désamidation",
                            "Trypsine, jusqu'à 1 clivage(s)",
                            "10,0 %",
                            "À examiner",
                            "Page 1 sur",
                            "https://www.maximeethier.com")) {
                assertTrue(text.contains(expected), "manquant : " + expected);
            }
        }

        @Test
        void minimalReport() throws IOException {
            QcReport report = QcReport.builder(ANALYSIS).generatedAt(GENERATED).build();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new AntibodyService().writeReport(report, out);
            String text = text(out.toByteArray());
            assertTrue(text.contains("Liabilities"));
            assertTrue(text.contains("MabScope dev"));
            for (String absent :
                    List.of(
                            "Theoretical peptide map",
                            "Sequence coverage",
                            "Lot comparison",
                            "Notes",
                            "Analyst")) {
                assertFalse(text.contains(absent), "unexpected: " + absent);
            }
        }

        @Test
        void emptySections() throws IOException {
            AntibodyAnalysis clean =
                    new AntibodyService(new LiabilityScanner(List.of()))
                            .analyze(TRASTUZUMAB, NumberingScheme.CHOTHIA);
            List<String> many = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                many.add("AAAAAAAAK" + "A".repeat(i));
            }
            Quantification nothing =
                    new Quantification(List.of("Lot A"), List.of(), List.of(), many);
            QcReport report =
                    QcReport.builder(clean)
                            .quantification(nothing)
                            .comparison("Lot A", 2, List.of())
                            .build();
            String text = text(new PdfReportWriter().toBytes(report));
            assertTrue(text.contains("No liability was found."));
            assertTrue(text.contains("No liability site is covered"));
            assertTrue(text.contains("No site of the reference was measured."));
            assertTrue(text.contains("12 observed sequence(s)"));
            assertTrue(text.contains("…"));
            assertTrue(text.contains("Chothia"));
        }

        @Test
        void comparableLotsAndOtherProtease() throws IOException {
            Quantification q = SERVICE.quantify(ANALYSIS, lots());
            List<SiteComparison> rows = SERVICE.compareLots(q, "Lot A", 50);
            DigestionSettings lysC = new DigestionSettings(Protease.LYS_C, 0, 5, 40, false);
            QcReport report =
                    QcReport.builder(ANALYSIS)
                            .peptideMap(lysC, SERVICE.peptideMap(ANALYSIS, lysC))
                            .comparison("Lot A", 50, rows)
                            .build();
            String text = text(writer.toBytes(report));
            assertTrue(text.contains("Comparable"));
            assertTrue(text.contains("Lys-C, up to 0 missed cleavage(s)"));
            assertTrue(text.contains("cysteines not alkylated"));
        }

        @Test
        void letterPagesInNorthAmerica() {
            assertEquals(PDRectangle.LETTER, PdfReportWriter.pageSizeFor(Locale.US));
            assertEquals(PDRectangle.LETTER, PdfReportWriter.pageSizeFor(Locale.CANADA_FRENCH));
            assertEquals(PDRectangle.A4, PdfReportWriter.pageSizeFor(Locale.FRANCE));
            assertEquals(PDRectangle.A4, PdfReportWriter.pageSizeFor(Locale.ENGLISH));
        }

        @Test
        void validation() {
            QcReport report = QcReport.builder(ANALYSIS).build();
            assertThrows(
                    NullPointerException.class,
                    () -> writer.write(null, new ByteArrayOutputStream()));
            assertThrows(NullPointerException.class, () -> writer.write(report, null));
        }
    }

    @Nested
    class Builder {

        @Test
        void fileNameKeepsLettersAndDigitsOfAnyLanguage() {
            assertEquals("MabScope-Trastuzumab-QC.pdf", QcReport.fileName("Trastuzumab"));
            assertEquals("MabScope-Mab_1_é-QC.pdf", QcReport.fileName(" Mab 1/é "));
        }

        @Test
        void defaults() {
            QcReport report = QcReport.builder(ANALYSIS).build();
            assertEquals(Locale.ENGLISH, report.locale());
            assertEquals("dev", report.appVersion());
            assertTrue(report.analyst().isEmpty());
            assertTrue(report.notes().isEmpty());
            assertTrue(report.peptideMap().isEmpty());
            assertTrue(report.quantification().isEmpty());
            assertTrue(report.comparison().isEmpty());
            assertTrue(report.generatedAt().isAfter(GENERATED.minusSeconds(1)));
            assertEquals(ANALYSIS, report.analysis());
        }

        @Test
        void sections() {
            QcReport report = complete(Locale.FRENCH).analyst("  ").notes(null).build();
            assertTrue(report.analyst().isEmpty(), "blank analyst");
            assertTrue(report.notes().isEmpty());
            assertEquals(ZoneOffset.UTC, report.zone());
            assertEquals(GENERATED, report.generatedAt());
            assertEquals("1.0.0", report.appVersion());
            assertEquals("Lot A", report.comparison().orElseThrow().reference());
            assertEquals(2, report.comparison().orElseThrow().threshold());
            assertFalse(report.peptideMap().orElseThrow().peptides().isEmpty());
            QcReport trimmed = QcReport.builder(ANALYSIS).notes(" trimmed ").build();
            assertEquals("trimmed", trimmed.notes().orElseThrow());
        }

        @Test
        void validation() {
            QcReport.Builder b = QcReport.builder(ANALYSIS);
            assertThrows(NullPointerException.class, () -> QcReport.builder(null));
            assertThrows(NullPointerException.class, () -> b.locale(null));
            assertThrows(NullPointerException.class, () -> b.zone(null));
            assertThrows(NullPointerException.class, () -> b.generatedAt(null));
            assertThrows(NullPointerException.class, () -> b.appVersion(null));
            assertThrows(NullPointerException.class, () -> b.quantification(null));
            assertThrows(NullPointerException.class, () -> b.peptideMap(null, List.of()));
            assertThrows(NullPointerException.class, () -> b.comparison(null, 2, List.of()));
        }
    }

    @Nested
    class Texts {

        private final ReportTexts english = new ReportTexts(Locale.ENGLISH, ZoneOffset.UTC);
        private final ReportTexts french = new ReportTexts(Locale.FRENCH, ZoneOffset.UTC);

        @Test
        void formats() {
            assertEquals("12.5%", english.percent(12.46));
            assertEquals("12,5 %", french.percent(12.46));
            assertEquals("3.0 pts", english.points(3));
            assertEquals("1083.5353", english.mass(1083.53531));
            assertEquals("1083,5353", french.mass(1083.53531));
            assertTrue(english.dateTime(GENERATED).contains("2026"));
            assertEquals(Locale.FRENCH, french.locale());
        }

        @Test
        void languages() {
            assertEquals("High", english.of("risk", Risk.HIGH));
            assertEquals("Élevé", french.of("risk", Risk.HIGH));
            assertEquals("Page 2 of 3", english.get("footer.page", 2, 3));
            ReportTexts german = new ReportTexts(Locale.GERMAN, ZoneOffset.UTC);
            assertEquals("Deamidation", german.of("type", LiabilityType.DEAMIDATION));
            assertThrows(NullPointerException.class, () -> new ReportTexts(null, ZoneOffset.UTC));
            assertThrows(NullPointerException.class, () -> new ReportTexts(Locale.FRENCH, null));
        }

        @Test
        void everyValueIsTranslated() {
            for (ReportTexts texts : List.of(english, french)) {
                for (Risk r : Risk.values()) {
                    assertFalse(texts.of("risk", r).isBlank());
                }
                for (LiabilityType t : LiabilityType.values()) {
                    assertFalse(texts.of("type", t).isBlank());
                }
                for (NumberingScheme s : NumberingScheme.values()) {
                    assertFalse(texts.of("scheme", s).isBlank());
                }
                for (Protease p : Protease.values()) {
                    assertFalse(texts.of("protease", p).isBlank());
                }
            }
        }

        @Test
        void bothLanguagesHaveTheSameKeys() throws IOException {
            assertEquals(keys("report.properties"), keys("report_fr.properties"));
        }

        private static Set<String> keys(String file) throws IOException {
            Properties p = new Properties();
            try (InputStream in = ReportTexts.class.getResourceAsStream(file)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            return p.stringPropertyNames();
        }
    }

    @Nested
    class Canvas {

        private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        @Test
        void wrapBreaksLongSequences() throws IOException {
            List<String> lines = PdfCanvas.wrap("A".repeat(200), font, 9, 100);
            assertTrue(lines.size() > 1);
            assertEquals(200, String.join("", lines).length());
            assertEquals(List.of(""), PdfCanvas.wrap("", font, 9, 100));
            assertEquals(List.of("two words"), PdfCanvas.wrap("two  words", font, 9, 100));
            assertEquals(List.of("first", "second"), PdfCanvas.wrap("first second", font, 9, 30));
        }

        @Test
        void fitAndEncoding() throws IOException {
            assertEquals("short", PdfCanvas.fit("short", font, 9, 100));
            assertTrue(PdfCanvas.fit("x".repeat(100), font, 9, 50).endsWith("…"));
            assertEquals("? and a b", PdfCanvas.encodable("α and a\nb", font));
        }

        @Test
        void colors() {
            assertArrayEquals(new float[] {1, 0, 0}, PdfCanvas.rgb("#ff0000"));
        }
    }
}
