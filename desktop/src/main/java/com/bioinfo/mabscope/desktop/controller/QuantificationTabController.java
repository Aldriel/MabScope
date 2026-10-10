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

import com.bioinfo.mabscope.desktop.i18n.I18n;
import com.bioinfo.mabscope.desktop.i18n.Messages;
import com.bioinfo.mabscope.desktop.model.DesktopModel;
import com.bioinfo.mabscope.desktop.view.FilePicker;
import com.bioinfo.mabscope.ms.MsFormat;
import com.bioinfo.mabscope.ms.MsImport;
import com.bioinfo.mabscope.quant.ChainCoverage;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.quant.SiteQuantity;
import com.bioinfo.proteomics.parser.ParseIssue;

import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Controller of the "LC-MS quantification" tab: imports the results of an MS software (MaxQuant,
 * FragPipe, Skyline or a generic table), then shows the sequence coverage, the modification level
 * of each liability site in each sample and the comparison of the lots with a reference.
 */
public final class QuantificationTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "quantification";

    /** Most unmatched sequences listed below the coverage. */
    static final int MAX_UNMATCHED_SHOWN = 10;

    /** Format chosen in the tab: detected from the columns, or imposed. */
    enum FormatChoice {
        /** Detected from the columns of the file. */
        AUTO(null),
        /** MaxQuant {@code evidence.txt}. */
        MAXQUANT(MsFormat.MAXQUANT),
        /** FragPipe {@code psm.tsv}. */
        FRAGPIPE(MsFormat.FRAGPIPE),
        /** Skyline report. */
        SKYLINE(MsFormat.SKYLINE),
        /** Generic table with ProForma sequences. */
        GENERIC(MsFormat.GENERIC);

        private final MsFormat format;

        FormatChoice(MsFormat format) {
            this.format = format;
        }

        /** Returns the imposed format, or {@code null} to detect it. */
        MsFormat format() {
            return format;
        }
    }

    private static final PseudoClass FLAGGED = PseudoClass.getPseudoClass("flagged");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final FilePicker filePicker;

    @FXML private Button importButton;
    @FXML private ComboBox<FormatChoice> formatBox;
    @FXML private Button clearButton;
    @FXML private Label summaryLabel;
    @FXML private TitledPane issuesPane;
    @FXML private ListView<String> issueList;
    @FXML private ComboBox<String> referenceBox;
    @FXML private Spinner<Double> thresholdSpinner;
    @FXML private Label comparisonSummaryLabel;
    @FXML private TableView<SiteComparison> comparisonTable;
    @FXML private TableView<SiteQuantity> siteTable;
    @FXML private TableColumn<SiteQuantity, String> siteChainColumn;
    @FXML private TableColumn<SiteQuantity, String> siteSiteColumn;
    @FXML private TableColumn<SiteQuantity, String> siteTypeColumn;
    @FXML private TableColumn<SiteQuantity, String> siteRegionColumn;
    @FXML private TableColumn<SiteQuantity, String> siteSampleColumn;
    @FXML private TableColumn<SiteQuantity, String> siteModifiedColumn;
    @FXML private TableColumn<SiteQuantity, String> siteFormsColumn;
    @FXML private TableView<ChainCoverage> coverageTable;
    @FXML private TableColumn<ChainCoverage, String> coverageChainColumn;
    @FXML private TableColumn<ChainCoverage, String> coverageSampleColumn;
    @FXML private TableColumn<ChainCoverage, String> coverageResiduesColumn;
    @FXML private TableColumn<ChainCoverage, String> coveragePercentColumn;
    @FXML private Label unmatchedLabel;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     * @param filePicker asks for the result file
     */
    public QuantificationTabController(
            DesktopModel model, I18n i18n, Consumer<String> showHelp, FilePicker filePicker) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
        this.filePicker = filePicker;
    }

    @FXML
    private void initialize() {
        Controls.translatedChoices(
                formatBox,
                FormatChoice.values(),
                FormatChoice.AUTO,
                choice -> "format." + choice.name(),
                i18n);
        clearButton.disableProperty().bind(model.msImportProperty().isNull());

        referenceBox.valueProperty().bindBidirectional(model.referenceProperty());
        thresholdSpinner.setValueFactory(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(
                        0.1, 100, model.thresholdProperty().get(), 0.5));
        thresholdSpinner
                .valueProperty()
                .addListener((obs, old, value) -> model.thresholdProperty().set(value));
        comparisonTable.setRowFactory(table -> new FlaggedRow());

        Controls.column(siteChainColumn, s -> s.site().chainName());
        Controls.column(siteSiteColumn, s -> Texts.site(s.site()));
        Controls.column(siteTypeColumn, s -> Texts.type(i18n.messages(), s.site().type()));
        Controls.column(siteRegionColumn, s -> Texts.region(i18n.messages(), s.site().region()));
        Controls.column(siteSampleColumn, SiteQuantity::sample);
        Controls.column(
                siteModifiedColumn, s -> Texts.percent(i18n.messages(), s.modifiedPercent()));
        Controls.column(siteFormsColumn, this::forms);

        Controls.column(coverageChainColumn, ChainCoverage::chainName);
        Controls.column(coverageSampleColumn, ChainCoverage::sample);
        Controls.column(coverageResiduesColumn, c -> c.coveredResidues() + " / " + c.length());
        Controls.column(coveragePercentColumn, c -> Texts.percent(i18n.messages(), c.percent()));

        model.msImportProperty().addListener((obs, old, imported) -> renderImport());
        model.resultsSourceProperty().addListener((obs, old, source) -> renderImport());
        model.analysisProperty().addListener((obs, old, analysis) -> renderImport());
        model.quantificationProperty().addListener((obs, old, q) -> renderQuantification());
        model.comparison()
                .addListener((ListChangeListener<SiteComparison>) change -> renderComparison());
        i18n.messagesProperty()
                .addListener(
                        (obs, old, messages) -> {
                            renderImport();
                            renderQuantification();
                            renderComparison();
                        });
        renderImport();
        renderQuantification();
        renderComparison();
    }

    /** Asks for a result file and imports it with the format chosen in the tab. */
    public void importFile() {
        filePicker
                .chooseResults(importButton.getScene().getWindow(), i18n.messages())
                .ifPresent(file -> model.importResults(file, formatBox.getValue().format()));
    }

    @FXML
    private void onImport() {
        importFile();
    }

    @FXML
    private void onDemo() {
        model.loadDemo();
    }

    @FXML
    private void onClear() {
        model.clearResults();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    private void renderImport() {
        MsImport imported = model.msImportProperty().get();
        Messages m = i18n.messages();
        if (imported == null) {
            summaryLabel.setText(m.get("quant.empty"));
            issueList.getItems().clear();
            issuesPane.setDisable(true);
            return;
        }
        String source = model.resultsSourceProperty().get();
        String summary =
                m.get(
                        "quant.summary",
                        "demo".equals(source) ? m.get("quant.demoSource") : source,
                        imported.format().label(),
                        imported.peptides().size(),
                        imported.samples().size(),
                        String.join(", ", imported.samples()),
                        imported.issues().size());
        if (model.analysisProperty().get() == null) {
            summary += " " + m.get("quant.needsAnalysis");
        }
        summaryLabel.setText(summary);
        issueList.getItems().setAll(imported.issues().stream().map(this::issue).toList());
        issuesPane.setDisable(imported.issues().isEmpty());
    }

    private String issue(ParseIssue issue) {
        Messages m = i18n.messages();
        return m.get(
                "quant.issue",
                m.get("issue." + issue.severity().name()),
                issue.location(),
                issue.message());
    }

    private void renderQuantification() {
        Quantification q = model.quantificationProperty().get();
        if (q == null) {
            referenceBox.getItems().clear();
            siteTable.getItems().clear();
            coverageTable.getItems().clear();
            unmatchedLabel.setText("");
            return;
        }
        String reference = model.referenceProperty().get();
        referenceBox.getItems().setAll(q.samples());
        model.referenceProperty().set(reference);
        siteTable.getItems().setAll(q.sites());
        siteTable.refresh();
        coverageTable.getItems().setAll(q.coverage());
        coverageTable.refresh();
        List<String> unmatched = q.unmatchedSequences();
        unmatchedLabel.setText(
                unmatched.isEmpty()
                        ? ""
                        : i18n.messages()
                                .get(
                                        "quant.unmatched",
                                        unmatched.size(),
                                        unmatched.stream()
                                                .limit(MAX_UNMATCHED_SHOWN)
                                                .collect(Collectors.joining(", "))));
    }

    /** Lists the forms of a site with their percentage, such as {@code Deamidated 2.1%}. */
    private String forms(SiteQuantity site) {
        return site.forms().keySet().stream()
                .map(form -> form + " " + Texts.percent(i18n.messages(), site.percent(form)))
                .collect(Collectors.joining(", "));
    }

    /** Rebuilds the comparison table: one column per sample, the reference first. */
    private void renderComparison() {
        Messages m = i18n.messages();
        List<SiteComparison> rows = model.comparison();
        comparisonTable.getColumns().clear();
        comparisonTable.getItems().setAll(rows);
        if (rows.isEmpty()) {
            comparisonSummaryLabel.setText("");
            return;
        }
        String reference = model.referenceProperty().get();
        comparisonTable
                .getColumns()
                .addAll(
                        column(m.get("column.chain"), c -> c.site().chainName()),
                        column(m.get("column.site"), c -> Texts.site(c.site())),
                        column(m.get("column.type"), c -> Texts.type(m, c.site().type())),
                        column(m.get("column.form"), SiteComparison::form));
        for (String sample : model.quantificationProperty().get().samples()) {
            String header =
                    sample.equals(reference) ? m.get("quant.referenceColumn", sample) : sample;
            comparisonTable
                    .getColumns()
                    .add(
                            column(
                                    header,
                                    c ->
                                            c.percents().containsKey(sample)
                                                    ? Texts.percent(m, c.percents().get(sample))
                                                    : "–"));
        }
        comparisonTable
                .getColumns()
                .addAll(
                        column(
                                m.get("column.deviation"),
                                c ->
                                        m.get(
                                                "format.points",
                                                String.format(
                                                        m.locale(), "%.1f", c.maxDeviation()))),
                        column(
                                m.get("column.status"),
                                c -> m.get(c.flagged() ? "quant.flagged" : "quant.comparable")));
        long flagged = rows.stream().filter(SiteComparison::flagged).count();
        comparisonSummaryLabel.setText(m.get("quant.comparisonSummary", flagged, rows.size()));
    }

    private static TableColumn<SiteComparison, String> column(
            String title, Function<SiteComparison, String> text) {
        TableColumn<SiteComparison, String> column = new TableColumn<>(title);
        Controls.column(column, text);
        return column;
    }

    /** Row of the comparison table, highlighted when the deviation exceeds the threshold. */
    private static final class FlaggedRow extends TableRow<SiteComparison> {
        @Override
        protected void updateItem(SiteComparison row, boolean empty) {
            super.updateItem(row, empty);
            pseudoClassStateChanged(FLAGGED, !empty && row != null && row.flagged());
        }
    }
}
