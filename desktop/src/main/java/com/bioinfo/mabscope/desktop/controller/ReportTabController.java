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
import com.bioinfo.mabscope.desktop.model.ReportOptions;
import com.bioinfo.mabscope.desktop.view.FilePicker;
import com.bioinfo.mabscope.report.QcReport;
import com.bioinfo.mabscope.service.AntibodyAnalysis;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Controller of the "QC report" tab: language, paper size, analyst, notes and optional sections of
 * the PDF quality control report, which is opened once saved.
 */
public final class ReportTabController {

    /** Section of the user guide describing this tab. */
    public static final String HELP_SECTION = "report";

    /** Paper size of the report; the country of the report locale chooses it. */
    enum Paper {
        /** US Letter, used in North America. */
        LETTER,
        /** A4, used elsewhere. */
        A4;

        /** Returns the paper used in the country of {@code locale}. */
        static Paper of(Locale locale) {
            return LETTER_COUNTRIES.contains(locale.getCountry()) ? LETTER : A4;
        }
    }

    /** Countries using the US Letter size, as in {@code PdfReportWriter}. */
    private static final Set<String> LETTER_COUNTRIES = Set.of("US", "CA");

    private final DesktopModel model;
    private final I18n i18n;
    private final Consumer<String> showHelp;
    private final FilePicker filePicker;
    private final Consumer<String> openUrl;
    private final Locale systemLocale;

    @FXML private ComboBox<Locale> languageBox;
    @FXML private ComboBox<Paper> paperBox;
    @FXML private TextField analystField;
    @FXML private TextArea notesArea;
    @FXML private CheckBox peptideMapBox;
    @FXML private CheckBox quantificationBox;
    @FXML private Button saveButton;
    @FXML private Label readinessLabel;

    /**
     * Creates the controller; the controls are injected afterwards by the FXML loader.
     *
     * @param model application state
     * @param i18n current language, also the default language of the report
     * @param showHelp opens the user guide at the given section
     * @param filePicker asks where to save the report
     * @param openUrl opens the saved report with the default PDF reader
     * @param systemLocale locale of the computer, whose country chooses the default paper size
     */
    public ReportTabController(
            DesktopModel model,
            I18n i18n,
            Consumer<String> showHelp,
            FilePicker filePicker,
            Consumer<String> openUrl,
            Locale systemLocale) {
        this.model = model;
        this.i18n = i18n;
        this.showHelp = showHelp;
        this.filePicker = filePicker;
        this.openUrl = openUrl;
        this.systemLocale = systemLocale;
    }

    @FXML
    private void initialize() {
        Controls.translatedChoices(
                languageBox,
                new Locale[] {Locale.ENGLISH, Locale.FRENCH},
                i18n.locale(),
                locale -> "reportLanguage." + locale.getLanguage(),
                i18n);
        Controls.translatedChoices(
                paperBox, Paper.values(), Paper.of(systemLocale), p -> "paper." + p.name(), i18n);
        saveButton.disableProperty().bind(model.analysisProperty().isNull());
        quantificationBox.disableProperty().bind(model.quantificationProperty().isNull());
        model.analysisProperty().addListener((obs, old, analysis) -> renderReadiness());
        i18n.messagesProperty().addListener((obs, old, messages) -> renderReadiness());
        renderReadiness();
    }

    /** Asks where to save the report, writes it and opens it. */
    public void save() {
        AntibodyAnalysis analysis = model.analysisProperty().get();
        if (analysis == null) {
            return;
        }
        Messages m = i18n.messages();
        filePicker
                .chooseReport(
                        saveButton.getScene().getWindow(),
                        m,
                        QcReport.fileName(analysis.antibody().name()))
                .ifPresent(
                        file ->
                                model.writeReport(
                                        file,
                                        options(),
                                        saved -> openUrl.accept(saved.toUri().toString())));
    }

    /** Returns the content of the report chosen in the tab. */
    ReportOptions options() {
        Locale language = languageBox.getValue();
        String country =
                paperBox.getValue() == Paper.LETTER
                        ? "CA"
                        : (language.getLanguage().equals("fr") ? "FR" : "GB");
        return new ReportOptions(
                Locale.of(language.getLanguage(), country),
                analystField.getText(),
                notesArea.getText(),
                peptideMapBox.isSelected(),
                quantificationBox.isSelected() && !quantificationBox.isDisabled());
    }

    @FXML
    private void onSave() {
        save();
    }

    @FXML
    private void onHelp() {
        showHelp.accept(HELP_SECTION);
    }

    private void renderReadiness() {
        AntibodyAnalysis analysis = model.analysisProperty().get();
        Messages m = i18n.messages();
        readinessLabel.setText(
                analysis == null
                        ? m.get("report.needsAnalysis")
                        : m.get("report.ready", analysis.antibody().name()));
    }
}
