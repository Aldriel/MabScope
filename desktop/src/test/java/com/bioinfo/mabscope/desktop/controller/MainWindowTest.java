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

package com.bioinfo.mabscope.desktop.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.desktop.DesktopApp;
import com.bioinfo.mabscope.desktop.FxThread;
import com.bioinfo.mabscope.desktop.i18n.Messages;
import com.bioinfo.mabscope.desktop.model.DesktopModel;
import com.bioinfo.mabscope.desktop.model.UiSettings;
import com.bioinfo.mabscope.desktop.prefs.UserPreferences;
import com.bioinfo.mabscope.desktop.view.FilePicker;
import com.bioinfo.mabscope.examples.ExampleAntibodies;
import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.digest.Protease;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

/**
 * Functional tests of the main window: the real FXML views, controllers and model, driven like a
 * user would through buttons, menus and fields, on headless JavaFX. Only the file choosers are
 * replaced, by {@link FakeFilePicker}.
 */
class MainWindowTest {

    private static final int ANTIBODY = 0;
    private static final int PEPTIDE_MAP = 1;
    private static final int QUANTIFICATION = 2;
    private static final int REPORT = 3;

    private final Preferences node =
            Preferences.userRoot().node("mabscope-ui-test-" + UUID.randomUUID());
    private final List<String> openedUrls = new ArrayList<>();
    private final FakeFilePicker files = new FakeFilePicker();
    private final Messages en = Messages.forLocale(Locale.ENGLISH);

    @TempDir Path dir;

    private DesktopModel model;
    private Stage stage;
    private Scene scene;

    /** Answers the file choosers with the paths set by the test. */
    static final class FakeFilePicker implements FilePicker {
        Path results;
        Path report;
        String suggestedName;
        int calls;

        @Override
        public Optional<Path> chooseResults(Window owner, Messages m) {
            calls++;
            return Optional.ofNullable(results);
        }

        @Override
        public Optional<Path> chooseReport(Window owner, Messages m, String suggestedName) {
            calls++;
            this.suggestedName = suggestedName;
            return Optional.ofNullable(report);
        }
    }

    @BeforeAll
    static void startJavaFx() {
        FxThread.start();
    }

    @BeforeEach
    void openWindow() {
        FxThread.run(
                () -> {
                    UiSettings settings = new UiSettings(new UserPreferences(node));
                    settings.setLocale(Locale.ENGLISH);
                    model =
                            new DesktopModel(
                                    new AntibodyService(), Runnable::run, Runnable::run, "1.0");
                    scene =
                            DesktopApp.createScene(
                                    model, settings, openedUrls::add, files, 1200, 760);
                    stage = new Stage();
                    stage.setScene(scene);
                    stage.show();
                });
    }

    @AfterEach
    void closeWindow() throws BackingStoreException {
        FxThread.run(
                () -> {
                    controller().helpWindow().stage().hide();
                    stage.hide();
                });
        node.removeNode();
    }

    // ----- Helpers -----

    private MainController controller() {
        return (MainController) scene.getUserData();
    }

    private TabPane tabs() {
        return (TabPane) scene.lookup("#mainTabs");
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T find(int tab, String id) {
        Node found = tabs().getTabs().get(tab).getContent().lookup("#" + id);
        assertNotNull(found, id);
        return (T) found;
    }

    private String text(int tab, String id) {
        return FxThread.call(() -> MainWindowTest.this.<Labeled>find(tab, id).getText());
    }

    private void click(int tab, String label) {
        FxThread.run(
                () ->
                        tabs().getTabs().get(tab).getContent().lookupAll(".button").stream()
                                .map(ButtonBase.class::cast)
                                .filter(b -> label.equals(b.getText()))
                                .findFirst()
                                .orElseThrow(() -> new AssertionError("no button " + label))
                                .fire());
    }

    private void menu(String label) {
        FxThread.run(() -> menuItem(label).fire());
    }

    private MenuItem menuItem(String label) {
        MenuBar bar = (MenuBar) scene.lookup("#menuBar");
        return bar.getMenus().stream()
                .flatMap(MainWindowTest::items)
                .filter(item -> label.equals(item.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no menu item " + label));
    }

    private static Stream<MenuItem> items(MenuItem item) {
        return item instanceof Menu menu
                ? Stream.concat(
                        Stream.of(item), menu.getItems().stream().flatMap(MainWindowTest::items))
                : Stream.of(item);
    }

    private String status() {
        return FxThread.call(() -> ((Label) scene.lookup("#statusLabel")).getText());
    }

    private void loadDemo() {
        click(ANTIBODY, "Demonstration");
    }

    private void layout() {
        FxThread.run(
                () -> {
                    scene.getRoot().applyCss();
                    scene.getRoot().layout();
                });
    }

    private long rowsWith(TableView<?> table, String pseudoClass) {
        PseudoClass state = PseudoClass.getPseudoClass(pseudoClass);
        return table.lookupAll(".table-row-cell").stream()
                .filter(row -> row.getPseudoClassStates().contains(state))
                .count();
    }

    private Path demoFile() throws IOException {
        return Files.writeString(
                dir.resolve("lots.csv"), ExampleAntibodies.demoResults(), StandardCharsets.UTF_8);
    }

    // ----- Tests -----

    @Test
    void startsTranslatedAndReady() {
        assertEquals(en.get("status.ready"), status());
        assertEquals(
                List.of("Antibody", "Peptide map", "LC-MS quantification", "QC report"),
                FxThread.call(() -> tabs().getTabs().stream().map(t -> t.getText()).toList()));
        assertTrue(FxThread.call(() -> find(REPORT, "saveButton").isDisabled()));
        assertEquals(en.get("report.needsAnalysis"), text(REPORT, "readinessLabel"));
        assertEquals(en.get("quant.empty"), text(QUANTIFICATION, "summaryLabel"));
        assertEquals(
                "contact@maximeethier.com",
                FxThread.call(() -> ((Hyperlink) scene.lookup("#emailLink")).getText()));
    }

    @Test
    void factoryRejectsUnknownControllers() {
        ControllerFactory factory =
                new ControllerFactory(
                        model,
                        new UiSettings(new UserPreferences(node)),
                        url -> {},
                        DesktopApp.STYLESHEET,
                        files);
        assertThrows(IllegalArgumentException.class, () -> factory.call(String.class));
    }

    @Nested
    class AntibodyTab {

        @Test
        void demonstrationFillsEveryTab() {
            loadDemo();

            assertTrue(status().startsWith("Demonstration loaded"), status());
            assertTrue(text(ANTIBODY, "summaryLabel").startsWith("Trastuzumab (Kabat CDRs)"));
            assertEquals(
                    3,
                    FxThread.call(
                                    () ->
                                            MainWindowTest.this
                                                    .<VBox>find(ANTIBODY, "sequenceBox")
                                                    .getChildren())
                            .size(),
                    "two chains and the legend");
            assertEquals(
                    16,
                    FxThread.call(
                                    () ->
                                            MainWindowTest.this.<TableView<?>>find(
                                                    ANTIBODY, "regionTable"))
                            .getItems()
                            .size(),
                    "FR1-4, CDR1-3 and the constant domain of both chains");
            assertFalse(
                    FxThread.call(
                                    () ->
                                            MainWindowTest.this.<TableView<?>>find(
                                                    PEPTIDE_MAP, "peptideTable"))
                            .getItems()
                            .isEmpty());
            assertEquals(
                    "Lot A",
                    FxThread.call(
                            () ->
                                    MainWindowTest.this
                                            .<ComboBox<String>>find(QUANTIFICATION, "referenceBox")
                                            .getValue()));
            TableView<?> comparison = FxThread.call(() -> find(QUANTIFICATION, "comparisonTable"));
            assertFalse(comparison.getItems().isEmpty());
            assertEquals(
                    List.of(
                            "Chain",
                            "Site",
                            "Type",
                            "Form",
                            "Lot A (reference)",
                            "Lot B",
                            "Stressed 40C",
                            "Max. deviation",
                            "Status"),
                    FxThread.call(
                            () -> comparison.getColumns().stream().map(c -> c.getText()).toList()));
            assertFalse(FxThread.call(() -> find(REPORT, "saveButton").isDisabled()));
            assertFalse(FxThread.call(() -> find(REPORT, "quantificationBox").isDisabled()));
            assertEquals(en.get("report.ready", "Trastuzumab"), text(REPORT, "readinessLabel"));
        }

        @Test
        void sequenceViewMarksTheCdrsAndTheLiabilities() {
            loadDemo();
            layout();
            VBox sequences = FxThread.call(() -> find(ANTIBODY, "sequenceBox"));
            assertFalse(FxThread.call(() -> sequences.lookupAll(".residue-cdr")).isEmpty());
            assertFalse(FxThread.call(() -> sequences.lookupAll(".risk-high")).isEmpty());
            TableView<?> regions = FxThread.call(() -> find(ANTIBODY, "regionTable"));
            assertTrue(FxThread.call(() -> rowsWith(regions, "cdr")) > 0);
            TableView<?> liabilities = FxThread.call(() -> find(ANTIBODY, "liabilityTable"));
            assertFalse(FxThread.call(() -> liabilities.lookupAll(".risk-high")).isEmpty());
        }

        @Test
        @SuppressWarnings("unchecked")
        void liabilitiesAreFilteredByRegionAndRisk() {
            loadDemo();
            TableView<Liability> table = FxThread.call(() -> find(ANTIBODY, "liabilityTable"));
            int all = table.getItems().size();

            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<CheckBox>find(ANTIBODY, "cdrOnlyBox")
                                    .setSelected(true));
            assertTrue(table.getItems().size() < all);
            assertTrue(table.getItems().stream().allMatch(Liability::inCdr));

            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<ComboBox<AntibodyTabController.RiskFilter>>find(
                                            ANTIBODY, "riskFilterBox")
                                    .setValue(AntibodyTabController.RiskFilter.HIGH));
            assertFalse(table.getItems().isEmpty());
            assertTrue(table.getItems().stream().allMatch(l -> l.risk() == Risk.HIGH));

            FxThread.run(
                    () -> {
                        MainWindowTest.this
                                .<CheckBox>find(ANTIBODY, "cdrOnlyBox")
                                .setSelected(false);
                        MainWindowTest.this
                                .<ComboBox<AntibodyTabController.RiskFilter>>find(
                                        ANTIBODY, "riskFilterBox")
                                .setValue(AntibodyTabController.RiskFilter.MEDIUM);
                    });
            assertTrue(table.getItems().stream().allMatch(l -> l.risk() != Risk.LOW));
        }

        @Test
        @SuppressWarnings("unchecked")
        void pastedSequencesAreAnalyzedWithTheChosenScheme() {
            String heavy = ExampleAntibodies.trastuzumab().chains().getFirst().sequence();
            FxThread.run(
                    () -> {
                        MainWindowTest.this
                                .<TextInputControl>find(ANTIBODY, "nameField")
                                .setText("My mAb");
                        MainWindowTest.this
                                .<TextInputControl>find(ANTIBODY, "heavyArea")
                                .setText(heavy);
                        MainWindowTest.this
                                .<ComboBox<NumberingScheme>>find(ANTIBODY, "schemeBox")
                                .setValue(NumberingScheme.CHOTHIA);
                    });
            click(ANTIBODY, "Analyze");
            assertTrue(text(ANTIBODY, "summaryLabel").startsWith("My mAb (Chothia CDRs)"));
            assertTrue(status().startsWith("My mAb analyzed"), status());
        }

        @Test
        void invalidSequencesShowAnError() {
            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<TextInputControl>find(ANTIBODY, "heavyArea")
                                    .setText("123"));
            click(ANTIBODY, "Analyze");
            assertTrue(status().startsWith("Error: invalid input"), status());
            assertTrue(
                    FxThread.call(
                            () ->
                                    scene.lookup("#statusLabel")
                                            .getPseudoClassStates()
                                            .contains(PseudoClass.getPseudoClass("error"))));
            assertEquals("", text(ANTIBODY, "summaryLabel"));
        }

        @Test
        void examplesFillInTheSequences() {
            FxThread.run(
                    () ->
                            tabs()
                                    .getTabs()
                                    .get(ANTIBODY)
                                    .getContent()
                                    .lookupAll(".menu-button")
                                    .stream()
                                    .map(MenuButton.class::cast)
                                    .flatMap(b -> b.getItems().stream())
                                    .filter(i -> i.getText().equals("Adalimumab"))
                                    .findFirst()
                                    .orElseThrow()
                                    .fire());
            assertEquals(
                    "Adalimumab",
                    FxThread.call(
                            () ->
                                    MainWindowTest.this
                                            .<TextInputControl>find(ANTIBODY, "nameField")
                                            .getText()));
            menu("Trastuzumab");
            assertTrue(text(ANTIBODY, "summaryLabel").startsWith("Trastuzumab"));
        }
    }

    @Nested
    class PeptideMapTab {

        @Test
        @SuppressWarnings("unchecked")
        void selectingAPeptideListsItsModifiedForms() {
            loadDemo();
            TableView<MappedPeptide> peptides =
                    FxThread.call(() -> find(PEPTIDE_MAP, "peptideTable"));
            assertTrue(text(PEPTIDE_MAP, "summaryLabel").contains("peptides"));
            MappedPeptide withLiability =
                    peptides.getItems().stream()
                            .filter(p -> !p.liabilities().isEmpty())
                            .findFirst()
                            .orElseThrow();
            FxThread.run(() -> peptides.getSelectionModel().select(withLiability));
            TableView<?> forms = FxThread.call(() -> find(PEPTIDE_MAP, "formTable"));
            assertFalse(forms.getItems().isEmpty());

            FxThread.run(() -> tabs().getSelectionModel().select(PEPTIDE_MAP));
            layout();
            List<String> cells =
                    FxThread.call(
                            () ->
                                    forms.lookupAll(".table-cell").stream()
                                            .map(c -> ((Labeled) c).getText())
                                            .filter(t -> t != null && !t.isEmpty())
                                            .toList());
            assertTrue(cells.stream().anyMatch(t -> t.matches("[A-Z]\\d+")), cells.toString());
            assertTrue(
                    cells.stream().anyMatch(t -> t.matches("[+-]\\d+\\.\\d{4}")), cells.toString());

            FxThread.run(
                    () -> {
                        peptides.getContextMenu().getItems().getFirst().fire();
                        assertEquals(
                                withLiability.proForma(),
                                Clipboard.getSystemClipboard().getString());
                        forms.getSelectionModel().select(0);
                        forms.getContextMenu().getItems().getFirst().fire();
                        assertTrue(
                                Clipboard.getSystemClipboard().getString().contains("["),
                                "modified ProForma");
                    });
        }

        @Test
        @SuppressWarnings("unchecked")
        void digestionSettingsAndFilter() {
            loadDemo();
            TableView<MappedPeptide> peptides =
                    FxThread.call(() -> find(PEPTIDE_MAP, "peptideTable"));
            int tryptic = peptides.getItems().size();

            FxThread.run(
                    () -> {
                        MainWindowTest.this
                                .<ComboBox<Protease>>find(PEPTIDE_MAP, "proteaseBox")
                                .setValue(Protease.LYS_C);
                        MainWindowTest.this
                                .<Spinner<Integer>>find(PEPTIDE_MAP, "missedSpinner")
                                .getValueFactory()
                                .setValue(0);
                    });
            click(PEPTIDE_MAP, "Digest");
            assertTrue(status().startsWith("Peptide map:"), status());
            assertTrue(peptides.getItems().size() < tryptic);

            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<CheckBox>find(PEPTIDE_MAP, "liabilitiesOnlyBox")
                                    .setSelected(true));
            assertTrue(peptides.getItems().stream().noneMatch(p -> p.liabilities().isEmpty()));

            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<Spinner<Integer>>find(PEPTIDE_MAP, "minLengthSpinner")
                                    .getValueFactory()
                                    .setValue(90));
            click(PEPTIDE_MAP, "Digest");
            assertTrue(status().startsWith("Error: invalid input"), status());
        }

        @Test
        void emptyBeforeTheFirstAnalysis() {
            assertEquals("", text(PEPTIDE_MAP, "summaryLabel"));
            FxThread.run(
                    () -> {
                        TableView<?> peptides = find(PEPTIDE_MAP, "peptideTable");
                        peptides.getContextMenu().getItems().getFirst().fire();
                    });
            assertEquals(en.get("status.ready"), status(), "nothing to copy");
        }
    }

    @Nested
    class QuantificationTab {

        @Test
        void importsTheFileChosenThenClearsIt() throws IOException {
            loadDemo();
            files.results = demoFile();
            click(QUANTIFICATION, "Import results…");

            assertTrue(status().startsWith("102 peptides read in 3 samples"), status());
            assertTrue(text(QUANTIFICATION, "summaryLabel").startsWith("lots.csv"));
            assertTrue(FxThread.call(() -> find(QUANTIFICATION, "issuesPane").isDisabled()));
            assertFalse(
                    FxThread.call(
                                    () ->
                                            MainWindowTest.this.<TableView<?>>find(
                                                    QUANTIFICATION, "siteTable"))
                            .getItems()
                            .isEmpty());
            assertFalse(
                    FxThread.call(
                                    () ->
                                            MainWindowTest.this.<TableView<?>>find(
                                                    QUANTIFICATION, "coverageTable"))
                            .getItems()
                            .isEmpty());

            click(QUANTIFICATION, "Remove the results");
            assertEquals(en.get("quant.empty"), text(QUANTIFICATION, "summaryLabel"));
            assertTrue(
                    FxThread.call(
                                    () ->
                                            MainWindowTest.this.<TableView<?>>find(
                                                    QUANTIFICATION, "comparisonTable"))
                            .getItems()
                            .isEmpty());
            assertTrue(FxThread.call(() -> find(REPORT, "quantificationBox").isDisabled()));
        }

        @Test
        void cancellingTheChooserImportsNothing() {
            files.results = null;
            menu("Import LC-MS results…");
            assertEquals(1, files.calls);
            assertEquals(
                    QUANTIFICATION,
                    FxThread.call(() -> tabs().getSelectionModel().getSelectedIndex()));
            assertEquals(en.get("status.ready"), status());
        }

        @Test
        @SuppressWarnings("unchecked")
        void resultsBeforeTheAnalysisAndReadingIssues() throws IOException {
            files.results =
                    Files.writeString(
                            dir.resolve("mixed.csv"),
                            "sequence,sample,intensity\n"
                                    + "IYPTNGYTR,Lot A,100\n"
                                    + "IYPTN[Deamidated]GYTR,Lot A,5\n"
                                    + "PEPTIDEK,Lot A,100\n"
                                    + "IYPTNGYTR,Lot A,oops\n");
            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<ComboBox<QuantificationTabController.FormatChoice>>find(
                                            QUANTIFICATION, "formatBox")
                                    .setValue(QuantificationTabController.FormatChoice.GENERIC));
            click(QUANTIFICATION, "Import results…");
            assertTrue(
                    text(QUANTIFICATION, "summaryLabel").endsWith(en.get("quant.needsAnalysis")));
            assertFalse(FxThread.call(() -> find(QUANTIFICATION, "issuesPane").isDisabled()));
            ListView<String> issues = FxThread.call(() -> find(QUANTIFICATION, "issueList"));
            assertEquals(1, issues.getItems().size());
            assertTrue(
                    issues.getItems().getFirst().startsWith("ERROR – line"),
                    issues.getItems().toString());

            menu("Trastuzumab");
            assertTrue(
                    FxThread.call(
                            () -> ((TitledPane) find(QUANTIFICATION, "issuesPane")).isDisabled()),
                    "examples forget the results");
            files.results = dir.resolve("mixed.csv");
            click(QUANTIFICATION, "Import results…");
            assertEquals(
                    en.get("quant.unmatched", 1, "PEPTIDEK"),
                    text(QUANTIFICATION, "unmatchedLabel"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void referenceAndThresholdDriveTheComparison() {
            loadDemo();
            FxThread.run(() -> tabs().getSelectionModel().select(QUANTIFICATION));
            TableView<?> comparison = FxThread.call(() -> find(QUANTIFICATION, "comparisonTable"));
            layout();
            long flagged = FxThread.call(() -> rowsWith(comparison, "flagged"));
            assertTrue(flagged > 0);
            assertTrue(text(QUANTIFICATION, "comparisonSummaryLabel").contains(" of "));

            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<Spinner<Double>>find(QUANTIFICATION, "thresholdSpinner")
                                    .getValueFactory()
                                    .setValue(100.0));
            assertEquals(100.0, model.thresholdProperty().get());
            assertTrue(text(QUANTIFICATION, "comparisonSummaryLabel").startsWith("0 of "));

            FxThread.run(
                    () ->
                            MainWindowTest.this
                                    .<ComboBox<String>>find(QUANTIFICATION, "referenceBox")
                                    .setValue("Lot B"));
            assertEquals("Lot B", model.referenceProperty().get());
            assertTrue(
                    FxThread.call(
                            () ->
                                    comparison.getColumns().stream()
                                            .anyMatch(
                                                    c -> c.getText().equals("Lot B (reference)"))));
        }
    }

    @Nested
    class ReportTab {

        @Test
        @SuppressWarnings("unchecked")
        void savesTheReportOnA4InFrenchAndOpensIt() throws IOException {
            loadDemo();
            Path file = dir.resolve("report.pdf");
            files.report = file;
            FxThread.run(
                    () -> {
                        MainWindowTest.this
                                .<ComboBox<Locale>>find(REPORT, "languageBox")
                                .setValue(Locale.FRENCH);
                        MainWindowTest.this
                                .<ComboBox<ReportTabController.Paper>>find(REPORT, "paperBox")
                                .setValue(ReportTabController.Paper.A4);
                        MainWindowTest.this
                                .<TextInputControl>find(REPORT, "analystField")
                                .setText("M. Ethier");
                    });
            click(REPORT, "Save the PDF report…");

            assertEquals("MabScope-Trastuzumab-QC.pdf", files.suggestedName);
            assertTrue(Files.size(file) > 1000);
            assertEquals(List.of(file.toUri().toString()), openedUrls);
            assertTrue(status().startsWith("Report report.pdf saved"), status());
            assertEquals(595, pageWidth(file), 1, "A4");
        }

        @Test
        @SuppressWarnings("unchecked")
        void letterPaperFromTheMenu() throws IOException {
            loadDemo();
            Path file = dir.resolve("letter.pdf");
            files.report = file;
            FxThread.run(
                    () -> {
                        MainWindowTest.this
                                .<ComboBox<ReportTabController.Paper>>find(REPORT, "paperBox")
                                .setValue(ReportTabController.Paper.LETTER);
                        MainWindowTest.this
                                .<CheckBox>find(REPORT, "peptideMapBox")
                                .setSelected(false);
                    });
            menu("Save the QC report…");
            assertEquals(
                    REPORT, FxThread.call(() -> tabs().getSelectionModel().getSelectedIndex()));
            assertEquals(612, pageWidth(file), 1, "US Letter");
        }

        @Test
        void englishOnA4AndCancelledChooser() throws IOException {
            loadDemo();
            files.report = null;
            menu("Save the QC report…");
            assertTrue(openedUrls.isEmpty());

            Path file = dir.resolve("a4.pdf");
            files.report = file;
            FxThread.run(
                    () -> {
                        MainWindowTest.this
                                .<ComboBox<ReportTabController.Paper>>find(REPORT, "paperBox")
                                .setValue(ReportTabController.Paper.A4);
                    });
            click(REPORT, "Save the PDF report…");
            assertEquals(595, pageWidth(file), 1);
        }

        @Test
        void nothingToSaveWithoutAnAnalysis() {
            menu("Save the QC report…");
            assertEquals(0, files.calls);
        }

        @Test
        void paperFollowsTheCountry() {
            assertEquals(
                    ReportTabController.Paper.LETTER,
                    ReportTabController.Paper.of(Locale.CANADA_FRENCH));
            assertEquals(ReportTabController.Paper.LETTER, ReportTabController.Paper.of(Locale.US));
            assertEquals(ReportTabController.Paper.A4, ReportTabController.Paper.of(Locale.FRANCE));
            assertEquals(
                    ReportTabController.Paper.A4, ReportTabController.Paper.of(Locale.ENGLISH));
        }

        private static float pageWidth(Path file) throws IOException {
            try (PDDocument pdf = Loader.loadPDF(file.toFile())) {
                return pdf.getPage(0).getMediaBox().getWidth();
            }
        }
    }

    @Nested
    class MenusAndShortcuts {

        @Test
        void languageChangesEveryText() {
            loadDemo();
            menu("Français");
            assertEquals("Anticorps", FxThread.call(() -> tabs().getTabs().getFirst().getText()));
            assertTrue(status().startsWith("Démonstration chargée"), status());
            assertTrue(text(ANTIBODY, "summaryLabel").startsWith("Trastuzumab (CDR Kabat)"));
            assertTrue(
                    FxThread.call(
                                    () ->
                                            MainWindowTest.this
                                                    .<TableView<?>>find(
                                                            QUANTIFICATION, "comparisonTable")
                                                    .getColumns()
                                                    .getFirst()
                                                    .getText())
                            .equals("Chaîne"));
            assertTrue(text(REPORT, "readinessLabel").startsWith("Rapport"));
            menu("English");
            assertEquals("Antibody", FxThread.call(() -> tabs().getTabs().getFirst().getText()));
        }

        @Test
        void textSizeMenuAndShortcuts() {
            Parent root = scene.getRoot();
            menu("Large");
            String large = FxThread.call(root::getStyle);
            menu("Larger text");
            assertFalse(large.equals(FxThread.call(root::getStyle)));
            menu("Default text size");
            String normal = FxThread.call(root::getStyle);
            shortcut(KeyCode.ADD);
            shortcut(KeyCode.SUBTRACT);
            assertEquals(normal, FxThread.call(root::getStyle));
            shortcut(KeyCode.PLUS);
            shortcut(KeyCode.NUMPAD0);
            menu("Smaller text");
            assertFalse(normal.equals(FxThread.call(root::getStyle)));
        }

        @Test
        void ctrlDigitsSelectTheTabs() {
            shortcut(KeyCode.DIGIT3);
            assertEquals(
                    QUANTIFICATION,
                    FxThread.call(() -> tabs().getSelectionModel().getSelectedIndex()));
            shortcut(KeyCode.DIGIT4);
            shortcut(KeyCode.DIGIT2);
            shortcut(KeyCode.DIGIT1);
            assertEquals(
                    ANTIBODY, FxThread.call(() -> tabs().getSelectionModel().getSelectedIndex()));
        }

        @Test
        void newAnalysisClearsEverything() {
            loadDemo();
            FxThread.run(() -> tabs().getSelectionModel().select(REPORT));
            menu("New analysis");
            assertEquals(
                    ANTIBODY, FxThread.call(() -> tabs().getSelectionModel().getSelectedIndex()));
            assertEquals("", text(ANTIBODY, "summaryLabel"));
            assertEquals(en.get("status.ready"), status());
            menu("Load the demonstration");
            menu("Adalimumab");
            assertTrue(text(ANTIBODY, "summaryLabel").startsWith("Adalimumab"));
        }

        @Test
        void helpOpensTheSectionOfTheTab() {
            click(QUANTIFICATION, "?");
            assertTrue(FxThread.call(() -> controller().helpWindow().stage().isShowing()));
            FxThread.run(() -> controller().helpWindow().search("deamidation"));
            FxThread.run(() -> tabs().getSelectionModel().select(PEPTIDE_MAP));
            menu("User guide");
            for (int tab : List.of(ANTIBODY, PEPTIDE_MAP, REPORT)) {
                click(tab, "?");
            }
            assertTrue(FxThread.call(() -> controller().helpWindow().stage().isShowing()));
        }

        @Test
        void linksOpenTheWebsites() {
            menu(en.get("menu.help.imgt"));
            menu(en.get("menu.help.abysis"));
            menu(en.get("menu.help.proforma"));
            FxThread.run(
                    () -> {
                        scene.getRoot()
                                .lookupAll(".hyperlink")
                                .forEach(l -> ((Hyperlink) l).fire());
                        ((ButtonBase) scene.lookup("#kofiButton")).fire();
                    });
            assertEquals(3 + 4, openedUrls.size(), openedUrls.toString());
            assertTrue(openedUrls.contains("https://www.imgt.org"));
            assertTrue(openedUrls.contains("mailto:contact@maximeethier.com"));
        }

        private void shortcut(KeyCode key) {
            FxThread.run(
                    () ->
                            scene.getAccelerators()
                                    .get(new KeyCodeCombination(key, KeyCombination.SHORTCUT_DOWN))
                                    .run());
        }
    }
}
