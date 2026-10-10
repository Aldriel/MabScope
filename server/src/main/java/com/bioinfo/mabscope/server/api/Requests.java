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

package com.bioinfo.mabscope.server.api;

import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.SequenceText;
import com.bioinfo.mabscope.ms.ObservedPeptide;
import com.bioinfo.mabscope.server.HttpError;
import com.bioinfo.proteomics.digest.Protease;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bodies of the JSON requests. Missing optional fields take the defaults of the desktop
 * application; missing required fields are answered with status 400.
 */
public final class Requests {

    private Requests() {}

    /**
     * Returns a required field.
     *
     * @param <T> type of the field
     * @param value value of the field
     * @param field name of the field, for the error message
     * @return the value
     * @throws HttpError with status 400 if the value is {@code null}
     */
    public static <T> T required(T value, String field) {
        if (value == null) {
            throw new HttpError(400, "Missing field '" + field + "'");
        }
        return value;
    }

    /**
     * An antibody to analyze. Sequences may be pasted as FASTA; a chain left empty is omitted.
     *
     * @param name display name; {@code Antibody} when blank
     * @param heavy heavy chain sequence
     * @param light light chain sequence
     * @param scheme CDR definition; Kabat when absent
     */
    public record AntibodyRequest(String name, String heavy, String light, NumberingScheme scheme) {

        /**
         * Builds the antibody.
         *
         * @return the antibody, with one or two chains
         * @throws HttpError with status 400 if both sequences are empty
         * @throws IllegalArgumentException if a sequence holds an invalid residue
         */
        public Antibody toAntibody() {
            String heavyResidues = SequenceText.clean(heavy);
            String lightResidues = SequenceText.clean(light);
            List<Chain> chains = new ArrayList<>();
            if (!heavyResidues.isEmpty()) {
                chains.add(new Chain("Heavy chain", ChainType.HEAVY, heavyResidues));
            }
            if (!lightResidues.isEmpty()) {
                chains.add(new Chain("Light chain", ChainType.LIGHT, lightResidues));
            }
            if (chains.isEmpty()) {
                throw new HttpError(400, "Enter the sequence of at least one chain");
            }
            String displayName = name == null || name.isBlank() ? "Antibody" : name.strip();
            return new Antibody(displayName, chains);
        }

        /** Returns the CDR definition, Kabat by default. */
        public NumberingScheme schemeOrDefault() {
            return Objects.requireNonNullElse(scheme, NumberingScheme.KABAT);
        }
    }

    /**
     * Digestion of the peptide map; absent fields take the values of a typical tryptic map.
     *
     * @param protease enzyme
     * @param missedCleavages maximum number of missed cleavages
     * @param minLength shortest peptide kept
     * @param maxLength longest peptide kept
     * @param alkylatedCysteines whether the cysteines are carbamidomethylated
     */
    public record DigestionRequest(
            Protease protease,
            Integer missedCleavages,
            Integer minLength,
            Integer maxLength,
            Boolean alkylatedCysteines) {

        /** Returns the settings, completed with {@link DigestionSettings#trypsinDefaults()}. */
        public DigestionSettings toSettings() {
            DigestionSettings d = DigestionSettings.trypsinDefaults();
            return new DigestionSettings(
                    Objects.requireNonNullElse(protease, d.protease()),
                    Objects.requireNonNullElse(missedCleavages, d.missedCleavages()),
                    Objects.requireNonNullElse(minLength, d.minLength()),
                    Objects.requireNonNullElse(maxLength, d.maxLength()),
                    Objects.requireNonNullElse(alkylatedCysteines, d.alkylatedCysteines()));
        }
    }

    /**
     * Body of {@code POST /api/peptide-map}.
     *
     * @param antibody the antibody
     * @param digestion the digestion; a tryptic map when absent
     */
    public record PeptideMapRequest(AntibodyRequest antibody, DigestionRequest digestion) {

        /** Returns the digestion settings. */
        public DigestionSettings settings() {
            return digestion == null ? DigestionSettings.trypsinDefaults() : digestion.toSettings();
        }
    }

    /**
     * A peptide measured in a sample, as returned by {@code POST /api/ms/import}.
     *
     * @param sequence the peptide in ProForma notation
     * @param sample the sample
     * @param intensity its abundance
     */
    public record Observation(String sequence, String sample, double intensity) {

        /**
         * Converts the observation.
         *
         * @param parser reads the ProForma sequence
         * @return the observed peptide
         * @throws com.bioinfo.proteomics.parser.ParsingException if the sequence is malformed
         */
        public ObservedPeptide toObserved(PeptideSequenceParser parser) {
            return new ObservedPeptide(
                    parser.parse(required(sequence, "sequence")),
                    required(sample, "sample"),
                    intensity);
        }

        /** Converts a list of observations; a {@code null} list is empty. */
        static List<ObservedPeptide> toObserved(
                List<Observation> observations, PeptideSequenceParser parser) {
            List<ObservedPeptide> peptides = new ArrayList<>();
            if (observations != null) {
                for (Observation o : observations) {
                    peptides.add(required(o, "peptides[]").toObserved(parser));
                }
            }
            return peptides;
        }
    }

    /**
     * Body of {@code POST /api/quantify}.
     *
     * @param antibody the antibody
     * @param peptides the peptides measured in every sample
     * @param reference reference sample of the lot comparison; no comparison when blank
     * @param threshold comparability threshold in percentage points; 2 when absent
     */
    public record QuantifyRequest(
            AntibodyRequest antibody,
            List<Observation> peptides,
            String reference,
            Double threshold) {

        /** Returns the observed peptides. */
        public List<ObservedPeptide> observed(PeptideSequenceParser parser) {
            return Observation.toObserved(peptides, parser);
        }
    }

    /**
     * Body of {@code POST /api/report}: the content of the quality control report.
     *
     * @param antibody the antibody
     * @param locale language and country of the reader, such as {@code fr-CA}; English when blank
     * @param timeZone time zone of the reader, such as {@code America/Toronto}; UTC when blank
     * @param analyst name of the analyst, optional
     * @param notes free-text notes, optional
     * @param digestion includes the peptide map with these settings when present
     * @param peptides includes the quantification when present and not empty
     * @param reference includes the lot comparison with this reference when not blank
     * @param threshold comparability threshold in percentage points; 2 when absent
     */
    public record ReportRequest(
            AntibodyRequest antibody,
            String locale,
            String timeZone,
            String analyst,
            String notes,
            DigestionRequest digestion,
            List<Observation> peptides,
            String reference,
            Double threshold) {

        /** Returns the observed peptides. */
        public List<ObservedPeptide> observed(PeptideSequenceParser parser) {
            return Observation.toObserved(peptides, parser);
        }
    }
}
