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

import com.bioinfo.mabscope.examples.ExampleAntibodies;
import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.ms.MsFormat;
import com.bioinfo.mabscope.ms.MsImport;
import com.bioinfo.mabscope.quant.LotComparator;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.report.QcReport;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.digest.Protease;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * State of the desktop application (the Model of the MVC pattern): the sequences entered, the
 * analysis, the peptide map, the imported LC-MS results, their quantification and the lot
 * comparison. The controllers bind the views to its observable properties and call its operations.
 *
 * <p>The work is delegated to {@link AntibodyService}. Long operations run on the {@code
 * background} executor and publish their results on the {@code uiThread} executor, so that the
 * window stays responsive; the tests pass executors running everything immediately.
 */
public final class DesktopModel {

    /** Name of the sample used as reference of the demonstration. */
    public static final String DEMO_REFERENCE = "Lot A";

    private final AntibodyService service;
    private final Executor background;
    private final Executor uiThread;
    private final String appVersion;

    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty heavy = new SimpleStringProperty("");
    private final StringProperty light = new SimpleStringProperty("");
    private final ObjectProperty<NumberingScheme> scheme =
            new SimpleObjectProperty<>(NumberingScheme.KABAT);
    private final ObjectProperty<DigestionSettings> digestion =
            new SimpleObjectProperty<>(DigestionSettings.trypsinDefaults());
    private final StringProperty reference = new SimpleStringProperty("");
    private final DoubleProperty threshold =
            new SimpleDoubleProperty(LotComparator.DEFAULT_THRESHOLD);

    private final ReadOnlyObjectWrapper<AntibodyAnalysis> analysis = new ReadOnlyObjectWrapper<>();
    private final ObservableList<MappedPeptide> peptides = FXCollections.observableArrayList();
    // The read-only views are kept: they forward the changes to the listeners of the views only
    // while they are reachable.
    private final ObservableList<MappedPeptide> readOnlyPeptides =
            FXCollections.unmodifiableObservableList(peptides);
    private final ReadOnlyObjectWrapper<MsImport> msImport = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<String> resultsSource = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Quantification> quantification =
            new ReadOnlyObjectWrapper<>();
    private final ObservableList<SiteComparison> comparison = FXCollections.observableArrayList();
    private final ObservableList<SiteComparison> readOnlyComparison =
            FXCollections.unmodifiableObservableList(comparison);

    private final ReadOnlyObjectWrapper<StatusMessage> status =
            new ReadOnlyObjectWrapper<>(StatusMessage.READY);
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private int runningTasks;

    /**
     * Creates the model.
     *
     * @param service the analyses
     * @param background runs the long operations
     * @param uiThread runs the updates of the observable state (the JavaFX application thread)
     * @param appVersion version of the application, printed in the reports
     */
    public DesktopModel(
            AntibodyService service, Executor background, Executor uiThread, String appVersion) {
        this.service = Objects.requireNonNull(service, "service");
        this.background = Objects.requireNonNull(background, "background");
        this.uiThread = Objects.requireNonNull(uiThread, "uiThread");
        this.appVersion = Objects.requireNonNull(appVersion, "appVersion");
        reference.addListener((obs, old, value) -> compare());
        threshold.addListener((obs, old, value) -> compare());
    }

    // ----- Operations -----

    /** Analyzes the sequences entered, then computes their peptide map and quantification. */
    public void analyze() {
        Antibody antibody;
        try {
            antibody = Antibody.fromText(name.get(), heavy.get(), light.get());
        } catch (IllegalArgumentException e) {
            fail(e);
            return;
        }
        NumberingScheme cdrScheme = scheme.get();
        DigestionSettings settings = digestion.get();
        MsImport imported = msImport.get();
        runAsync(
                StatusMessage.info("status.analyzing", antibody.name()),
                () -> compute(antibody, cdrScheme, settings, imported),
                this::show);
    }

    /**
     * Fills in the sequences of an example antibody and analyzes it. The LC-MS results of the
     * previous antibody are forgotten.
     *
     * @param example the example, such as {@link ExampleAntibodies#trastuzumab()}
     */
    public void loadExample(Antibody example) {
        clearResults();
        name.set(example.name());
        heavy.set(sequence(example, ChainType.HEAVY));
        light.set(sequence(example, ChainType.LIGHT));
        analyze();
    }

    /**
     * Loads the demonstration: trastuzumab and simulated LC-MS results of three samples, compared
     * with {@value #DEMO_REFERENCE}.
     */
    public void loadDemo() {
        Antibody example = ExampleAntibodies.trastuzumab();
        name.set(example.name());
        heavy.set(sequence(example, ChainType.HEAVY));
        light.set(sequence(example, ChainType.LIGHT));
        NumberingScheme cdrScheme = scheme.get();
        DigestionSettings settings = digestion.get();
        runAsync(
                StatusMessage.info("status.loadingDemo"),
                () -> {
                    MsImport imported =
                            service.importResults(
                                    new StringReader(ExampleAntibodies.demoResults()));
                    return compute(example, cdrScheme, settings, imported);
                },
                results -> {
                    resultsSource.set("demo");
                    show(results);
                    reference.set(DEMO_REFERENCE);
                    status.set(
                            StatusMessage.info(
                                    "status.demoLoaded", results.imported().samples().size()));
                });
    }

    /**
     * Computes the peptide map again with the settings entered by the user; invalid settings, such
     * as a minimum length above the maximum, are reported in the status bar.
     *
     * @param protease enzyme
     * @param missedCleavages maximum number of missed cleavages
     * @param minLength shortest peptide kept
     * @param maxLength longest peptide kept
     * @param alkylatedCysteines whether the cysteines are carbamidomethylated
     */
    public void digest(
            Protease protease,
            int missedCleavages,
            int minLength,
            int maxLength,
            boolean alkylatedCysteines) {
        DigestionSettings settings;
        try {
            settings =
                    new DigestionSettings(
                            protease, missedCleavages, minLength, maxLength, alkylatedCysteines);
        } catch (IllegalArgumentException e) {
            fail(e);
            return;
        }
        digest(settings);
    }

    /**
     * Computes the peptide map again with new digestion settings.
     *
     * @param settings protease, missed cleavages, peptide lengths and alkylation
     */
    public void digest(DigestionSettings settings) {
        digestion.set(Objects.requireNonNull(settings, "settings"));
        AntibodyAnalysis current = analysis.get();
        if (current == null) {
            return;
        }
        runAsync(
                StatusMessage.info("status.digesting"),
                () -> service.peptideMap(current, settings),
                map -> {
                    peptides.setAll(map);
                    status.set(StatusMessage.info("status.digested", map.size()));
                });
    }

    /**
     * Imports the results of an MS software, then quantifies them if an antibody is analyzed.
     *
     * @param file the result file
     * @param format its format, or {@code null} to detect it
     */
    public void importResults(Path file, MsFormat format) {
        AntibodyAnalysis current = analysis.get();
        runAsync(
                StatusMessage.info("status.importing", file.getFileName()),
                () -> {
                    MsImport imported;
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        imported =
                                format == null
                                        ? service.importResults(reader)
                                        : service.importResults(reader, format);
                    }
                    Quantification q =
                            current == null ? null : service.quantify(current, imported.peptides());
                    return new Imported(imported, q);
                },
                result -> {
                    resultsSource.set(file.getFileName().toString());
                    msImport.set(result.imported());
                    quantification.set(result.quantification());
                    selectReference(result.imported().samples());
                    compare();
                    status.set(
                            StatusMessage.info(
                                    result.imported().issues().isEmpty()
                                            ? "status.imported"
                                            : "status.importedWithIssues",
                                    result.imported().peptides().size(),
                                    result.imported().samples().size(),
                                    result.imported().issues().size()));
                });
    }

    /**
     * Writes the quality control report of the current analysis.
     *
     * @param file the PDF file to write
     * @param options content of the report
     * @param onSaved called on the UI thread once the file is written
     */
    public void writeReport(Path file, ReportOptions options, Consumer<Path> onSaved) {
        AntibodyAnalysis current = analysis.get();
        if (current == null) {
            status.set(StatusMessage.error("error.noAnalysis"));
            return;
        }
        QcReport report = buildReport(current, options);
        runAsync(
                StatusMessage.info("status.writingReport"),
                () -> {
                    try (OutputStream out = Files.newOutputStream(file)) {
                        service.writeReport(report, out);
                    }
                    return file;
                },
                saved -> {
                    status.set(StatusMessage.info("status.reportSaved", saved.getFileName()));
                    onSaved.accept(saved);
                });
    }

    /** Forgets the sequences and every result. */
    public void clear() {
        name.set("");
        heavy.set("");
        light.set("");
        analysis.set(null);
        peptides.clear();
        clearResults();
        status.set(StatusMessage.READY);
    }

    /** Forgets the imported LC-MS results, their quantification and the lot comparison. */
    public void clearResults() {
        msImport.set(null);
        resultsSource.set(null);
        quantification.set(null);
        reference.set("");
        comparison.clear();
    }

    // ----- Computations -----

    /** Results of {@link #analyze()}: everything that depends on the sequences. */
    private record Results(
            AntibodyAnalysis analysis,
            List<MappedPeptide> peptides,
            MsImport imported,
            Quantification quantification) {}

    /** Results of {@link #importResults}: the import and, with an analysis, its quantification. */
    private record Imported(MsImport imported, Quantification quantification) {}

    private Results compute(
            Antibody antibody,
            NumberingScheme cdrScheme,
            DigestionSettings settings,
            MsImport imported) {
        AntibodyAnalysis result = service.analyze(antibody, cdrScheme);
        List<MappedPeptide> map = service.peptideMap(result, settings);
        Quantification q = imported == null ? null : service.quantify(result, imported.peptides());
        return new Results(result, map, imported, q);
    }

    private void show(Results results) {
        analysis.set(results.analysis());
        peptides.setAll(results.peptides());
        msImport.set(results.imported());
        quantification.set(results.quantification());
        if (results.imported() != null) {
            selectReference(results.imported().samples());
        }
        compare();
        List<Liability> liabilities = results.analysis().liabilities();
        status.set(
                StatusMessage.info(
                        "status.analyzed",
                        results.analysis().antibody().name(),
                        liabilities.size(),
                        liabilities.stream().filter(l -> l.risk() == Risk.HIGH).count()));
    }

    /** Keeps the reference when it is one of the samples, otherwise selects the first sample. */
    private void selectReference(List<String> samples) {
        if (!samples.contains(reference.get())) {
            reference.set(samples.isEmpty() ? "" : samples.getFirst());
        }
    }

    /** Compares the samples with the reference; called when the reference or threshold change. */
    private void compare() {
        Quantification q = quantification.get();
        String ref = reference.get();
        if (q == null || ref == null || !q.samples().contains(ref)) {
            comparison.clear();
            return;
        }
        comparison.setAll(service.compareLots(q, ref, threshold.get()));
    }

    private QcReport buildReport(AntibodyAnalysis current, ReportOptions options) {
        QcReport.Builder report =
                QcReport.builder(current)
                        .locale(options.locale())
                        .zone(ZoneId.systemDefault())
                        .generatedAt(Instant.now())
                        .appVersion(appVersion)
                        .analyst(options.analyst())
                        .notes(options.notes());
        if (options.includePeptideMap()) {
            report.peptideMap(digestion.get(), List.copyOf(peptides));
        }
        Quantification q = quantification.get();
        if (options.includeQuantification() && q != null) {
            report.quantification(q);
            if (!comparison.isEmpty()) {
                report.comparison(reference.get(), threshold.get(), List.copyOf(comparison));
            }
        }
        return report.build();
    }

    private static String sequence(Antibody antibody, ChainType type) {
        return antibody.chains().stream()
                .filter(c -> c.type() == type)
                .map(Chain::sequence)
                .findFirst()
                .orElse("");
    }

    // ----- Background tasks -----

    private <T> void runAsync(StatusMessage progress, Callable<T> work, Consumer<T> onSuccess) {
        runningTasks++;
        busy.set(true);
        status.set(progress);
        background.execute(
                () -> {
                    try {
                        T result = work.call();
                        uiThread.execute(
                                () -> {
                                    endTask();
                                    onSuccess.accept(result);
                                });
                    } catch (Exception e) {
                        uiThread.execute(
                                () -> {
                                    endTask();
                                    fail(e);
                                });
                    }
                });
    }

    private void endTask() {
        runningTasks--;
        busy.set(runningTasks > 0);
    }

    private void fail(Exception e) {
        status.set(StatusMessage.fromException(e));
    }

    // ----- Observable state -----

    /** Returns the name of the antibody entered. */
    public StringProperty nameProperty() {
        return name;
    }

    /** Returns the heavy chain entered, possibly as FASTA. */
    public StringProperty heavyProperty() {
        return heavy;
    }

    /** Returns the light chain entered, possibly as FASTA. */
    public StringProperty lightProperty() {
        return light;
    }

    /** Returns the CDR definition used by the next analysis. */
    public ObjectProperty<NumberingScheme> schemeProperty() {
        return scheme;
    }

    /** Returns the digestion settings of the peptide map. */
    public ReadOnlyObjectProperty<DigestionSettings> digestionProperty() {
        return digestion;
    }

    /** Returns the reference sample of the lot comparison; the comparison follows its changes. */
    public StringProperty referenceProperty() {
        return reference;
    }

    /** Returns the comparability threshold, in percentage points. */
    public DoubleProperty thresholdProperty() {
        return threshold;
    }

    /** Returns the last analysis, or {@code null}. */
    public ReadOnlyObjectProperty<AntibodyAnalysis> analysisProperty() {
        return analysis.getReadOnlyProperty();
    }

    /** Returns the peptides of the theoretical map, read-only. */
    public ObservableList<MappedPeptide> peptides() {
        return readOnlyPeptides;
    }

    /** Returns the imported LC-MS results, or {@code null}. */
    public ReadOnlyObjectProperty<MsImport> msImportProperty() {
        return msImport.getReadOnlyProperty();
    }

    /** Returns the name of the imported file, {@code demo} for the demonstration, or null. */
    public ReadOnlyObjectProperty<String> resultsSourceProperty() {
        return resultsSource.getReadOnlyProperty();
    }

    /** Returns the coverage and modification levels, or {@code null}. */
    public ReadOnlyObjectProperty<Quantification> quantificationProperty() {
        return quantification.getReadOnlyProperty();
    }

    /** Returns the comparison of the samples with the reference, read-only. */
    public ObservableList<SiteComparison> comparison() {
        return readOnlyComparison;
    }

    /** Returns the message shown in the status bar. */
    public ReadOnlyObjectProperty<StatusMessage> statusProperty() {
        return status.getReadOnlyProperty();
    }

    /** Returns whether a background operation is running. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy;
    }
}
