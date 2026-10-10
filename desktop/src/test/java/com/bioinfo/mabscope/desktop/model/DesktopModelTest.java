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

package com.bioinfo.mabscope.desktop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.examples.ExampleAntibodies;
import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.ms.MsFormat;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.digest.Protease;

import javafx.collections.ListChangeListener;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;

class DesktopModelTest {

    private static final String VERSION = "9.9.9";

    private final DesktopModel model =
            new DesktopModel(new AntibodyService(), Runnable::run, Runnable::run, VERSION);

    private String statusKey() {
        return model.statusProperty().get().key();
    }

    private static Path demoFile(Path dir) throws IOException {
        return Files.writeString(
                dir.resolve("lots.csv"), ExampleAntibodies.demoResults(), StandardCharsets.UTF_8);
    }

    @Test
    void startsEmptyAndReady() {
        assertEquals(StatusMessage.READY, model.statusProperty().get());
        assertNull(model.analysisProperty().get());
        assertTrue(model.peptides().isEmpty());
        assertFalse(model.busyProperty().get());
        assertEquals(NumberingScheme.KABAT, model.schemeProperty().get());
        assertEquals(DigestionSettings.trypsinDefaults(), model.digestionProperty().get());
        assertThrows(
                NullPointerException.class,
                () -> new DesktopModel(null, Runnable::run, Runnable::run, VERSION));
    }

    @Nested
    class Analysis {

        @Test
        void pastedSequencesAreAnalyzedAndDigested() {
            model.nameProperty().set("  Herceptin ");
            model.heavyProperty().set(">heavy\n" + trastuzumabHeavy());
            model.analyze();

            assertEquals("status.analyzed", statusKey());
            assertEquals("Herceptin", model.analysisProperty().get().antibody().name());
            assertEquals(1, model.analysisProperty().get().chains().size(), "heavy chain only");
            assertFalse(model.peptides().isEmpty());
            assertNull(model.quantificationProperty().get());
            assertFalse(model.busyProperty().get());
        }

        @Test
        void missingSequencesAreReported() {
            model.analyze();
            assertEquals("error.invalidInput", statusKey());
            assertNull(model.analysisProperty().get());
        }

        @Test
        void aChainThatIsNotAnAntibodyIsReported() {
            model.heavyProperty().set("MKTAYIAKQRQISFVKSHFSRQLEERLGLIEVQAPILSRVGDGTQDNLSGAEK");
            model.analyze();
            assertEquals("error.notAnAntibody", statusKey());
            assertTrue(model.statusProperty().get().error());
        }

        @Test
        void examplesReplaceTheSequencesAndForgetTheResults(@TempDir Path dir) throws IOException {
            model.loadDemo();
            model.loadExample(ExampleAntibodies.adalimumab());
            assertEquals("Adalimumab", model.nameProperty().get());
            assertFalse(model.heavyProperty().get().isEmpty());
            assertFalse(model.lightProperty().get().isEmpty());
            assertNull(model.msImportProperty().get());
            assertNull(model.quantificationProperty().get());
            assertTrue(model.comparison().isEmpty());
            assertEquals("Adalimumab", model.analysisProperty().get().antibody().name());
        }

        @Test
        void schemeIsUsedByTheNextAnalysis() {
            model.schemeProperty().set(NumberingScheme.CHOTHIA);
            model.loadExample(ExampleAntibodies.trastuzumab());
            assertEquals(NumberingScheme.CHOTHIA, model.analysisProperty().get().scheme());
        }
    }

    @Nested
    class Demonstration {

        @Test
        void loadsTheLotsAndComparesThemWithLotA() {
            model.loadDemo();

            assertEquals("status.demoLoaded", statusKey());
            assertEquals("demo", model.resultsSourceProperty().get());
            assertEquals(DesktopModel.DEMO_REFERENCE, model.referenceProperty().get());
            assertEquals(3, model.quantificationProperty().get().samples().size());
            assertFalse(model.comparison().isEmpty());
            assertTrue(model.comparison().stream().anyMatch(SiteComparison::flagged));
        }

        @Test
        void referenceAndThresholdUpdateTheComparison() {
            model.loadDemo();
            long flagged = model.comparison().stream().filter(SiteComparison::flagged).count();

            model.thresholdProperty().set(100);
            assertTrue(model.comparison().stream().noneMatch(SiteComparison::flagged));
            model.thresholdProperty().set(0.1);
            assertTrue(
                    model.comparison().stream().filter(SiteComparison::flagged).count() >= flagged);

            List<SiteComparison> withLotA = List.copyOf(model.comparison());
            model.referenceProperty().set("Lot B");
            assertFalse(model.comparison().isEmpty());
            assertFalse(withLotA.equals(model.comparison()));
            model.referenceProperty().set("Unknown lot");
            assertTrue(model.comparison().isEmpty(), "unknown reference: no comparison");
            model.referenceProperty().set(null);
            assertTrue(model.comparison().isEmpty());
        }

        @Test
        void reanalyzingKeepsTheImportedResults() {
            model.loadDemo();
            model.referenceProperty().set("Lot B");
            model.analyze();
            assertNotNull(model.quantificationProperty().get());
            assertEquals("Lot B", model.referenceProperty().get(), "reference kept");
            assertFalse(model.comparison().isEmpty());
        }

        @Test
        void clearForgetsEverything() {
            model.loadDemo();
            model.clear();
            assertEquals("", model.nameProperty().get());
            assertEquals("", model.heavyProperty().get());
            assertNull(model.analysisProperty().get());
            assertTrue(model.peptides().isEmpty());
            assertNull(model.msImportProperty().get());
            assertNull(model.resultsSourceProperty().get());
            assertTrue(model.comparison().isEmpty());
            assertEquals(StatusMessage.READY, model.statusProperty().get());
        }
    }

    @Nested
    class PeptideMap {

        @Test
        void digestionSettingsRecomputeTheMap() {
            model.loadExample(ExampleAntibodies.trastuzumab());
            int tryptic = model.peptides().size();

            model.digest(Protease.LYS_C, 0, 5, 40, true);
            assertEquals("status.digested", statusKey());
            assertEquals(Protease.LYS_C, model.digestionProperty().get().protease());
            assertTrue(model.peptides().size() < tryptic, "Lys-C cuts less often than trypsin");
            assertTrue(
                    model.peptides().stream()
                            .allMatch(
                                    p ->
                                            p.sequence().length() >= 5
                                                    && p.sequence().length() <= 40));
        }

        @Test
        void invalidSettingsAreReported() {
            model.loadExample(ExampleAntibodies.trastuzumab());
            DigestionSettings before = model.digestionProperty().get();
            model.digest(Protease.TRYPSIN, 1, 30, 10, true);
            assertEquals("error.invalidInput", statusKey());
            assertEquals(before, model.digestionProperty().get());
        }

        @Test
        void settingsAreKeptUntilTheFirstAnalysis() {
            DigestionSettings settings = new DigestionSettings(Protease.GLU_C, 2, 4, 50, false);
            model.digest(settings);
            assertTrue(model.peptides().isEmpty());
            assertEquals(StatusMessage.READY, model.statusProperty().get());
            model.loadExample(ExampleAntibodies.trastuzumab());
            assertEquals(settings, model.digestionProperty().get());
            assertThrows(NullPointerException.class, () -> model.digest(null));
        }
    }

    @Nested
    class Import {

        @Test
        void resultsImportedBeforeTheAnalysisAreQuantifiedByIt(@TempDir Path dir)
                throws IOException {
            model.importResults(demoFile(dir), null);
            assertEquals("status.imported", statusKey());
            assertEquals("lots.csv", model.resultsSourceProperty().get());
            assertEquals(MsFormat.GENERIC, model.msImportProperty().get().format());
            assertNull(model.quantificationProperty().get(), "nothing to quantify yet");
            assertEquals("Lot A", model.referenceProperty().get(), "first sample by default");

            model.heavyProperty().set(trastuzumabHeavy());
            model.analyze();
            assertNotNull(model.quantificationProperty().get());
            assertFalse(model.comparison().isEmpty());
        }

        @Test
        void resultsImportedAfterTheAnalysisAreQuantifiedAtOnce(@TempDir Path dir)
                throws IOException {
            model.loadExample(ExampleAntibodies.trastuzumab());
            model.importResults(demoFile(dir), MsFormat.GENERIC);
            assertNotNull(model.quantificationProperty().get());
            assertFalse(model.comparison().isEmpty());
        }

        @Test
        void readingIssuesAreCounted(@TempDir Path dir) throws IOException {
            Path file =
                    Files.writeString(
                            dir.resolve("bad.csv"),
                            "sequence,sample,intensity\nIYPTNGYTR,Lot A,100\nIYPTNGYTR,Lot A,x\n");
            model.importResults(file, null);
            assertEquals("status.importedWithIssues", statusKey());
            assertEquals(1, model.msImportProperty().get().issues().size());
        }

        @Test
        void unreadableFilesAreReported(@TempDir Path dir) {
            model.importResults(dir.resolve("missing.csv"), null);
            assertEquals("error.file", statusKey());
            assertNull(model.msImportProperty().get());
            assertFalse(model.busyProperty().get());
        }

        @Test
        void clearResultsKeepsTheAnalysis(@TempDir Path dir) throws IOException {
            model.loadExample(ExampleAntibodies.trastuzumab());
            model.importResults(demoFile(dir), null);
            model.clearResults();
            assertNotNull(model.analysisProperty().get());
            assertNull(model.quantificationProperty().get());
            assertEquals("", model.referenceProperty().get());
        }
    }

    @Nested
    class Report {

        private final ReportOptions everything =
                new ReportOptions(Locale.CANADA_FRENCH, "M. Ethier", "Lots A et B", true, true);

        @Test
        void writesThePdfAndCallsBack(@TempDir Path dir) throws IOException {
            model.loadDemo();
            Path file = dir.resolve("report.pdf");
            List<Path> saved = new ArrayList<>();
            model.writeReport(file, everything, saved::add);

            assertEquals(List.of(file), saved);
            assertEquals("status.reportSaved", statusKey());
            String text = pdfText(file);
            assertTrue(text.contains("Trastuzumab"), text);
            assertTrue(text.contains("M. Ethier"));
            assertTrue(text.contains("Lot B"), "comparison included");
            assertTrue(text.contains(VERSION));
        }

        @Test
        void optionalSectionsCanBeLeftOut(@TempDir Path dir) throws IOException {
            model.loadDemo();
            Path full = dir.resolve("full.pdf");
            Path minimal = dir.resolve("minimal.pdf");
            model.writeReport(full, everything, p -> {});
            model.writeReport(
                    minimal, new ReportOptions(Locale.ENGLISH, "", "", false, false), p -> {});
            assertTrue(pdfText(minimal).length() < pdfText(full).length());
            assertFalse(pdfText(minimal).contains("Lot B"));
        }

        @Test
        void quantificationWithoutComparisonIsPrinted(@TempDir Path dir) throws IOException {
            model.loadDemo();
            model.referenceProperty().set("Unknown lot");
            Path file = dir.resolve("report.pdf");
            model.writeReport(file, everything, p -> {});
            assertEquals("status.reportSaved", statusKey());
        }

        @Test
        void needsAnAnalysis(@TempDir Path dir) {
            List<Path> saved = new ArrayList<>();
            model.writeReport(dir.resolve("r.pdf"), everything, saved::add);
            assertEquals("error.noAnalysis", statusKey());
            assertTrue(saved.isEmpty());
        }

        @Test
        void writeFailuresAreReported(@TempDir Path dir) {
            model.loadExample(ExampleAntibodies.trastuzumab());
            List<Path> saved = new ArrayList<>();
            model.writeReport(dir.resolve("missing/dir/r.pdf"), everything, saved::add);
            assertEquals("error.file", statusKey());
            assertTrue(saved.isEmpty());
        }

        private static String pdfText(Path file) throws IOException {
            try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
                return new PDFTextStripper().getText(pdf);
            }
        }
    }

    @Test
    void listenersOfTheReadOnlyListsSurviveGarbageCollection() {
        List<String> changes = new ArrayList<>();
        model.comparison().addListener((ListChangeListener<SiteComparison>) c -> changes.add("c"));
        model.peptides().addListener((ListChangeListener<MappedPeptide>) c -> changes.add("p"));
        System.gc();
        model.loadDemo();
        assertTrue(changes.contains("c") && changes.contains("p"), changes.toString());
        assertSame(model.comparison(), model.comparison());
    }

    @Test
    void busyWhileATaskRuns() {
        Queue<Runnable> pending = new ArrayDeque<>();
        DesktopModel queued =
                new DesktopModel(new AntibodyService(), pending::add, Runnable::run, VERSION);
        queued.loadExample(ExampleAntibodies.trastuzumab());
        queued.loadDemo();

        assertTrue(queued.busyProperty().get());
        assertEquals("status.loadingDemo", queued.statusProperty().get().key());
        pending.poll().run();
        assertTrue(queued.busyProperty().get(), "the demonstration still runs");
        pending.poll().run();
        assertFalse(queued.busyProperty().get());
        assertEquals("status.demoLoaded", queued.statusProperty().get().key());
    }

    private static String trastuzumabHeavy() {
        return ExampleAntibodies.trastuzumab().chains().getFirst().sequence();
    }
}
