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
import com.bioinfo.mabscope.examples.ExampleAntibodies;
import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.RegionSpan;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.ChainAnalysis;

import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Controller of the "Antibody" tab: sequences of the chains, their regions, an annotated view of
 * the sequences and the liabilities, which can be filtered by region and risk.
 */
public final class AntibodyTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "antibody";

    /** Liabilities shown in the table, by minimum risk. */
    enum RiskFilter {
        /** Every liability. */
        ALL(Risk.LOW),
        /** Medium and high risk. */
        MEDIUM(Risk.MEDIUM),
        /** High risk only. */
        HIGH(Risk.HIGH);

        private final Risk minimum;

        RiskFilter(Risk minimum) {
            this.minimum = minimum;
        }

        boolean accepts(Risk risk) {
            return risk.compareTo(minimum) >= 0;
        }
    }

    /**
     * A row of the region table.
     *
     * @param chain name of the chain
     * @param span the region and its positions
     * @param sequence residues of the region
     */
    record RegionRow(String chain, RegionSpan span, String sequence) {}

    private static final PseudoClass CDR = PseudoClass.getPseudoClass("cdr");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;

    @FXML private TextField nameField;
    @FXML private ComboBox<NumberingScheme> schemeBox;
    @FXML private TextArea heavyArea;
    @FXML private TextArea lightArea;
    @FXML private Label summaryLabel;
    @FXML private VBox sequenceBox;
    @FXML private TableView<RegionRow> regionTable;
    @FXML private TableColumn<RegionRow, String> regionChainColumn;
    @FXML private TableColumn<RegionRow, String> regionNameColumn;
    @FXML private TableColumn<RegionRow, String> regionPositionsColumn;
    @FXML private TableColumn<RegionRow, Integer> regionLengthColumn;
    @FXML private TableColumn<RegionRow, String> regionSequenceColumn;
    @FXML private CheckBox cdrOnlyBox;
    @FXML private ComboBox<RiskFilter> riskFilterBox;
    @FXML private TableView<Liability> liabilityTable;
    @FXML private TableColumn<Liability, String> liabilityChainColumn;
    @FXML private TableColumn<Liability, String> liabilitySiteColumn;
    @FXML private TableColumn<Liability, String> liabilityTypeColumn;
    @FXML private TableColumn<Liability, String> liabilityMotifColumn;
    @FXML private TableColumn<Liability, String> liabilityRegionColumn;
    @FXML private TableColumn<Liability, Risk> liabilityRiskColumn;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     */
    public AntibodyTabController(DesktopModel model, I18n i18n, Consumer<String> showHelp) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
    }

    @FXML
    private void initialize() {
        nameField.textProperty().bindBidirectional(model.nameProperty());
        heavyArea.textProperty().bindBidirectional(model.heavyProperty());
        lightArea.textProperty().bindBidirectional(model.lightProperty());
        Controls.translatedChoices(
                schemeBox,
                NumberingScheme.values(),
                model.schemeProperty().get(),
                scheme -> "scheme." + scheme.name(),
                i18n);
        schemeBox.valueProperty().bindBidirectional(model.schemeProperty());
        Controls.translatedChoices(
                riskFilterBox,
                RiskFilter.values(),
                RiskFilter.ALL,
                filter -> "riskFilter." + filter.name(),
                i18n);

        Controls.column(regionChainColumn, RegionRow::chain);
        Controls.column(
                regionNameColumn, row -> Texts.region(i18n.messages(), row.span().region()));
        Controls.column(
                regionPositionsColumn,
                row -> Texts.positions(row.span().start(), row.span().end()));
        Controls.column(regionLengthColumn, row -> row.span().length());
        Controls.column(regionSequenceColumn, RegionRow::sequence);
        regionTable.setRowFactory(table -> new CdrRow());

        Controls.column(liabilityChainColumn, Liability::chainName);
        Controls.column(liabilitySiteColumn, Texts::site);
        Controls.column(liabilityTypeColumn, l -> Texts.type(i18n.messages(), l.type()));
        Controls.column(liabilityMotifColumn, Liability::motif);
        Controls.column(liabilityRegionColumn, l -> Texts.region(i18n.messages(), l.region()));
        Controls.column(liabilityRiskColumn, Liability::risk);
        liabilityRiskColumn.setCellFactory(column -> new RiskCell());

        model.analysisProperty().addListener((obs, old, analysis) -> render());
        cdrOnlyBox.selectedProperty().addListener((obs, old, selected) -> renderLiabilities());
        riskFilterBox.valueProperty().addListener((obs, old, filter) -> renderLiabilities());
        i18n.messagesProperty().addListener((obs, old, messages) -> render());
        render();
    }

    @FXML
    private void onAnalyze() {
        model.analyze();
    }

    @FXML
    private void onTrastuzumab() {
        model.loadExample(ExampleAntibodies.trastuzumab());
    }

    @FXML
    private void onAdalimumab() {
        model.loadExample(ExampleAntibodies.adalimumab());
    }

    @FXML
    private void onDemo() {
        model.loadDemo();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    /** Displays the last analysis, or empties the results if there is none. */
    private void render() {
        AntibodyAnalysis analysis = model.analysisProperty().get();
        sequenceBox.getChildren().clear();
        if (analysis == null) {
            summaryLabel.setText("");
            regionTable.getItems().clear();
            liabilityTable.getItems().clear();
            return;
        }
        Messages m = i18n.messages();
        List<Liability> liabilities = analysis.liabilities();
        summaryLabel.setText(
                m.get(
                        "antibody.summary",
                        analysis.antibody().name(),
                        m.get("scheme." + analysis.scheme().name()),
                        liabilities.size(),
                        count(liabilities, Risk.HIGH),
                        count(liabilities, Risk.MEDIUM),
                        liabilities.stream().filter(Liability::inCdr).count()));
        List<RegionRow> rows = new ArrayList<>();
        for (ChainAnalysis chain : analysis.chains()) {
            sequenceBox.getChildren().add(SequenceView.render(chain, m));
            for (RegionSpan span : chain.annotation().spans()) {
                rows.add(
                        new RegionRow(
                                chain.chain().name(),
                                span,
                                chain.chain().subsequence(span.start(), span.end())));
            }
        }
        sequenceBox.getChildren().add(legend(m));
        regionTable.getItems().setAll(rows);
        renderLiabilities();
    }

    private void renderLiabilities() {
        AntibodyAnalysis analysis = model.analysisProperty().get();
        if (analysis == null) {
            return;
        }
        RiskFilter filter = riskFilterBox.getValue();
        liabilityTable
                .getItems()
                .setAll(
                        analysis.liabilities().stream()
                                .filter(l -> !cdrOnlyBox.isSelected() || l.inCdr())
                                .filter(l -> filter.accepts(l.risk()))
                                .toList());
    }

    private static long count(List<Liability> liabilities, Risk risk) {
        return liabilities.stream().filter(l -> l.risk() == risk).count();
    }

    /** Legend of the colors of the sequence view. */
    private static Label legend(Messages m) {
        Label legend = new Label(m.get("antibody.legend"));
        legend.getStyleClass().addAll("muted", "small");
        legend.setWrapText(true);
        return legend;
    }

    /** Row of the region table, highlighted when the region is a CDR. */
    private static final class CdrRow extends TableRow<RegionRow> {
        @Override
        protected void updateItem(RegionRow row, boolean empty) {
            super.updateItem(row, empty);
            pseudoClassStateChanged(CDR, !empty && row != null && row.span().region().isCdr());
        }
    }

    /** Cell showing a translated risk level in the color of the level. */
    private final class RiskCell extends TableCell<Liability, Risk> {
        @Override
        protected void updateItem(Risk risk, boolean empty) {
            super.updateItem(risk, empty);
            getStyleClass().removeAll("risk-high", "risk-medium", "risk-low");
            if (empty || risk == null) {
                setText(null);
                return;
            }
            setText(Texts.risk(i18n.messages(), risk));
            getStyleClass().add("risk-" + risk.name().toLowerCase(Locale.ROOT));
        }
    }
}
