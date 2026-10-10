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
import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.mapping.ModifiedForm;
import com.bioinfo.proteomics.digest.Protease;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Controller of the "Peptide map" tab: digests the antibody in silico and lists its peptides, with
 * the liabilities they contain and the masses of their modified forms, to look for in the LC-MS
 * data.
 */
public final class PeptideMapTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "peptide-map";

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;

    /** Peptide whose modified forms are listed, or {@code null}. */
    private MappedPeptide formsPeptide;

    @FXML private ComboBox<Protease> proteaseBox;
    @FXML private Spinner<Integer> missedSpinner;
    @FXML private Spinner<Integer> minLengthSpinner;
    @FXML private Spinner<Integer> maxLengthSpinner;
    @FXML private CheckBox alkylatedBox;
    @FXML private CheckBox liabilitiesOnlyBox;
    @FXML private Label summaryLabel;
    @FXML private TableView<MappedPeptide> peptideTable;
    @FXML private TableColumn<MappedPeptide, String> chainColumn;
    @FXML private TableColumn<MappedPeptide, String> positionsColumn;
    @FXML private TableColumn<MappedPeptide, String> sequenceColumn;
    @FXML private TableColumn<MappedPeptide, Integer> missedColumn;
    @FXML private TableColumn<MappedPeptide, String> massColumn;
    @FXML private TableColumn<MappedPeptide, String> regionsColumn;
    @FXML private TableColumn<MappedPeptide, String> liabilitiesColumn;
    @FXML private TableView<ModifiedForm> formTable;
    @FXML private TableColumn<ModifiedForm, String> formSiteColumn;
    @FXML private TableColumn<ModifiedForm, String> formTypeColumn;
    @FXML private TableColumn<ModifiedForm, String> formModificationColumn;
    @FXML private TableColumn<ModifiedForm, String> formMassColumn;
    @FXML private TableColumn<ModifiedForm, String> formDeltaColumn;
    @FXML private TableColumn<ModifiedForm, String> formProFormaColumn;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language
     * @param showHelp opens the user guide at the given section
     */
    public PeptideMapTabController(DesktopModel model, I18n i18n, Consumer<String> showHelp) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
    }

    @FXML
    private void initialize() {
        DigestionSettings settings = model.digestionProperty().get();
        Controls.translatedChoices(
                proteaseBox,
                Protease.values(),
                settings.protease(),
                protease -> "protease." + protease.name(),
                i18n);
        missedSpinner.getValueFactory().setValue(settings.missedCleavages());
        minLengthSpinner.getValueFactory().setValue(settings.minLength());
        maxLengthSpinner.getValueFactory().setValue(settings.maxLength());
        alkylatedBox.setSelected(settings.alkylatedCysteines());

        Controls.column(chainColumn, MappedPeptide::chainName);
        Controls.column(positionsColumn, p -> Texts.positions(p.start(), p.end()));
        Controls.column(sequenceColumn, MappedPeptide::sequence);
        Controls.column(missedColumn, MappedPeptide::missedCleavages);
        Controls.column(massColumn, p -> Texts.mass(p.monoisotopicMass()));
        Controls.column(
                regionsColumn,
                p ->
                        p.regions().stream()
                                .map(r -> Texts.region(i18n.messages(), r))
                                .collect(Collectors.joining(", ")));
        Controls.column(
                liabilitiesColumn,
                p ->
                        p.liabilities().stream()
                                .map(
                                        l ->
                                                Texts.site(l)
                                                        + " "
                                                        + Texts.type(i18n.messages(), l.type()))
                                .collect(Collectors.joining(", ")));
        peptideTable.setContextMenu(
                new ContextMenu(
                        Controls.menuItem(
                                "map.copyProForma",
                                i18n,
                                () -> copySelected(peptideTable, MappedPeptide::proForma))));

        Controls.column(formSiteColumn, this::site);
        Controls.column(formTypeColumn, f -> Texts.type(i18n.messages(), f.type()));
        Controls.column(formModificationColumn, ModifiedForm::modification);
        Controls.column(formMassColumn, f -> Texts.mass(f.monoisotopicMass()));
        Controls.column(formDeltaColumn, this::delta);
        Controls.column(formProFormaColumn, ModifiedForm::proForma);
        formTable.setContextMenu(
                new ContextMenu(
                        Controls.menuItem(
                                "map.copyProForma",
                                i18n,
                                () -> copySelected(formTable, ModifiedForm::proForma))));

        peptideTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, peptide) -> showForms(peptide));
        model.peptides().addListener((ListChangeListener<MappedPeptide>) change -> render());
        liabilitiesOnlyBox.selectedProperty().addListener((obs, old, selected) -> render());
        i18n.messagesProperty()
                .addListener(
                        (obs, old, messages) -> {
                            render();
                            peptideTable.refresh();
                            formTable.refresh();
                        });
        render();
    }

    @FXML
    private void onDigest() {
        model.digest(
                proteaseBox.getValue(),
                Controls.spinnerValue(missedSpinner),
                Controls.spinnerValue(minLengthSpinner),
                Controls.spinnerValue(maxLengthSpinner),
                alkylatedBox.isSelected());
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    private void render() {
        List<MappedPeptide> peptides = model.peptides();
        List<MappedPeptide> shown =
                liabilitiesOnlyBox.isSelected()
                        ? peptides.stream().filter(p -> !p.liabilities().isEmpty()).toList()
                        : peptides;
        peptideTable.getItems().setAll(shown);
        Messages m = i18n.messages();
        summaryLabel.setText(
                peptides.isEmpty()
                        ? ""
                        : m.get(
                                "map.summary",
                                peptides.size(),
                                peptides.stream().filter(p -> !p.liabilities().isEmpty()).count(),
                                peptides.stream().filter(MappedPeptide::coversCdr).count()));
        showForms(peptideTable.getSelectionModel().getSelectedItem());
    }

    private void showForms(MappedPeptide peptide) {
        formsPeptide = peptide;
        formTable.getItems().setAll(peptide == null ? List.of() : peptide.modifiedForms());
    }

    /** Residue and position of the modified site, such as {@code N55}. */
    private String site(ModifiedForm form) {
        return formsPeptide.sequence().charAt(form.position() - formsPeptide.start())
                + String.valueOf(form.position());
    }

    /** Mass shift of a modified form relative to the unmodified peptide, with its sign. */
    private String delta(ModifiedForm form) {
        double delta = form.monoisotopicMass() - formsPeptide.monoisotopicMass();
        return (delta >= 0 ? "+" : "") + Texts.mass(delta);
    }

    private static <T> void copySelected(TableView<T> table, Function<T, String> text) {
        T selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            Controls.copyToClipboard(text.apply(selected));
        }
    }
}
