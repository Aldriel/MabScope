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

import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.service.AntibodyAnalysis;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The content of a quality control report, assembled with a {@link Builder} (GoF pattern): the
 * analysis of the antibody is required, while the peptide map, the quantification, the lot
 * comparison and the notes are optional sections, included only when they are provided.
 *
 * <p>The desktop application and the web server build the same report, rendered by {@link
 * PdfReportWriter}.
 */
public final class QcReport {

    private final AntibodyAnalysis analysis;
    private final Locale locale;
    private final ZoneId zone;
    private final Instant generatedAt;
    private final String appVersion;
    private final String analyst;
    private final String notes;
    private final PeptideMapSection peptideMap;
    private final Quantification quantification;
    private final ComparisonSection comparison;

    private QcReport(Builder b) {
        this.analysis = b.analysis;
        this.locale = b.locale;
        this.zone = b.zone;
        this.generatedAt = b.generatedAt;
        this.appVersion = b.appVersion;
        this.analyst = b.analyst;
        this.notes = b.notes;
        this.peptideMap = b.peptideMap;
        this.quantification = b.quantification;
        this.comparison = b.comparison;
    }

    /**
     * Starts a report.
     *
     * @param analysis regions and liabilities of the antibody
     * @return a builder, in English, dated now, without optional sections
     */
    public static Builder builder(AntibodyAnalysis analysis) {
        return new Builder(analysis);
    }

    /** Returns the analysis of the antibody. */
    public AntibodyAnalysis analysis() {
        return analysis;
    }

    /** Returns the language of the report. */
    public Locale locale() {
        return locale;
    }

    /** Returns the time zone of the dates. */
    public ZoneId zone() {
        return zone;
    }

    /** Returns the generation time. */
    public Instant generatedAt() {
        return generatedAt;
    }

    /** Returns the version of the application, shown in the footer. */
    public String appVersion() {
        return appVersion;
    }

    /** Returns the name of the analyst, if given. */
    public Optional<String> analyst() {
        return Optional.ofNullable(analyst);
    }

    /** Returns the free-text notes, if given. */
    public Optional<String> notes() {
        return Optional.ofNullable(notes);
    }

    /** Returns the peptide map section, if included. */
    public Optional<PeptideMapSection> peptideMap() {
        return Optional.ofNullable(peptideMap);
    }

    /** Returns the measured modification levels, if included. */
    public Optional<Quantification> quantification() {
        return Optional.ofNullable(quantification);
    }

    /** Returns the lot comparison section, if included. */
    public Optional<ComparisonSection> comparison() {
        return Optional.ofNullable(comparison);
    }

    /**
     * The theoretical peptide map and the digestion that produced it.
     *
     * @param settings enzyme, missed cleavages, lengths and alkylation
     * @param peptides the peptides of every chain
     */
    public record PeptideMapSection(DigestionSettings settings, List<MappedPeptide> peptides) {

        /** Validates the section. */
        public PeptideMapSection {
            Objects.requireNonNull(settings, "settings");
            peptides = List.copyOf(peptides);
        }
    }

    /**
     * The comparison of the lots with a reference.
     *
     * @param reference name of the reference sample
     * @param threshold comparability threshold, in percentage points
     * @param rows one comparison per form of each site
     */
    public record ComparisonSection(String reference, double threshold, List<SiteComparison> rows) {

        /** Validates the section. */
        public ComparisonSection {
            Objects.requireNonNull(reference, "reference");
            rows = List.copyOf(rows);
        }
    }

    /** Assembles a {@link QcReport} step by step. */
    public static final class Builder {

        private final AntibodyAnalysis analysis;
        private Locale locale = Locale.ENGLISH;
        private ZoneId zone = ZoneId.systemDefault();
        private Instant generatedAt = Instant.now();
        private String appVersion = "dev";
        private String analyst;
        private String notes;
        private PeptideMapSection peptideMap;
        private Quantification quantification;
        private ComparisonSection comparison;

        private Builder(AntibodyAnalysis analysis) {
            this.analysis = Objects.requireNonNull(analysis, "analysis");
        }

        /**
         * Sets the language; French, or English for any other language.
         *
         * @param locale the language
         * @return this builder
         */
        public Builder locale(Locale locale) {
            this.locale = Objects.requireNonNull(locale, "locale");
            return this;
        }

        /**
         * Sets the time zone of the dates.
         *
         * @param zone the time zone of the reader
         * @return this builder
         */
        public Builder zone(ZoneId zone) {
            this.zone = Objects.requireNonNull(zone, "zone");
            return this;
        }

        /**
         * Sets the generation time.
         *
         * @param generatedAt the time shown in the report
         * @return this builder
         */
        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = Objects.requireNonNull(generatedAt, "generatedAt");
            return this;
        }

        /**
         * Sets the version of the application.
         *
         * @param appVersion version shown in the footer
         * @return this builder
         */
        public Builder appVersion(String appVersion) {
            this.appVersion = Objects.requireNonNull(appVersion, "appVersion");
            return this;
        }

        /**
         * Sets the name of the analyst.
         *
         * @param analyst the name; blank to omit it
         * @return this builder
         */
        public Builder analyst(String analyst) {
            this.analyst = blankToNull(analyst);
            return this;
        }

        /**
         * Adds free-text notes at the end of the report.
         *
         * @param notes the notes; blank to omit them
         * @return this builder
         */
        public Builder notes(String notes) {
            this.notes = blankToNull(notes);
            return this;
        }

        /**
         * Includes the theoretical peptide map.
         *
         * @param settings digestion that produced the peptides
         * @param peptides the peptides of every chain
         * @return this builder
         */
        public Builder peptideMap(DigestionSettings settings, List<MappedPeptide> peptides) {
            this.peptideMap = new PeptideMapSection(settings, peptides);
            return this;
        }

        /**
         * Includes the coverage and the modification levels measured by LC-MS.
         *
         * @param quantification the quantities of each site in each sample
         * @return this builder
         */
        public Builder quantification(Quantification quantification) {
            this.quantification = Objects.requireNonNull(quantification, "quantification");
            return this;
        }

        /**
         * Includes the comparison of the lots with a reference.
         *
         * @param reference name of the reference sample
         * @param threshold comparability threshold, in percentage points
         * @param rows the comparisons
         * @return this builder
         */
        public Builder comparison(String reference, double threshold, List<SiteComparison> rows) {
            this.comparison = new ComparisonSection(reference, threshold, rows);
            return this;
        }

        /** Returns the report. */
        public QcReport build() {
            return new QcReport(this);
        }

        private static String blankToNull(String text) {
            return text == null || text.isBlank() ? null : text.strip();
        }
    }
}
