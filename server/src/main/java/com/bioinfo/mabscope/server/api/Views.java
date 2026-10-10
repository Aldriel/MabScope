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

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.LiabilityType;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.mapping.ModifiedForm;
import com.bioinfo.mabscope.model.ChainType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.model.RegionSpan;
import com.bioinfo.mabscope.ms.MsFormat;
import com.bioinfo.mabscope.ms.MsImport;
import com.bioinfo.mabscope.quant.ChainCoverage;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.quant.SiteQuantity;
import com.bioinfo.mabscope.server.api.Requests.Observation;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.ChainAnalysis;
import com.bioinfo.proteomics.parser.ParseIssue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bodies of the JSON responses: flat, explicit views of the model, so that the React client does
 * not depend on the internal classes.
 */
public final class Views {

    private Views() {}

    /**
     * Response of {@code POST /api/analysis}.
     *
     * @param name name of the antibody
     * @param scheme CDR definition used
     * @param chains chains and their regions
     * @param liabilities liabilities of every chain
     */
    public record AnalysisView(
            String name,
            NumberingScheme scheme,
            List<ChainView> chains,
            List<LiabilityView> liabilities) {

        /** Creates the view of an analysis. */
        public static AnalysisView of(AntibodyAnalysis analysis) {
            return new AnalysisView(
                    analysis.antibody().name(),
                    analysis.scheme(),
                    analysis.chains().stream().map(ChainView::of).toList(),
                    analysis.liabilities().stream().map(LiabilityView::of).toList());
        }
    }

    /**
     * A chain and its regions.
     *
     * @param name name of the chain
     * @param type heavy or light
     * @param sequence residues
     * @param length number of residues
     * @param regions frameworks, CDRs and constant region, in order
     */
    public record ChainView(
            String name, ChainType type, String sequence, int length, List<RegionView> regions) {

        static ChainView of(ChainAnalysis chain) {
            return new ChainView(
                    chain.chain().name(),
                    chain.chain().type(),
                    chain.chain().sequence(),
                    chain.chain().length(),
                    chain.annotation().spans().stream()
                            .map(
                                    s ->
                                            RegionView.of(
                                                    s,
                                                    chain.chain().subsequence(s.start(), s.end())))
                            .toList());
        }
    }

    /**
     * A region of a chain.
     *
     * @param region framework, CDR or constant region
     * @param start 1-based position of its first residue
     * @param end 1-based position of its last residue
     * @param cdr whether it is a CDR
     * @param sequence its residues
     */
    public record RegionView(Region region, int start, int end, boolean cdr, String sequence) {

        static RegionView of(RegionSpan span, String sequence) {
            return new RegionView(
                    span.region(), span.start(), span.end(), span.region().isCdr(), sequence);
        }
    }

    /**
     * A liability.
     *
     * @param chain name of the chain
     * @param type kind of modification
     * @param position 1-based position of the modified residue
     * @param motif residues defining the site
     * @param region region of the site
     * @param risk likelihood that the site affects the product
     * @param inCdr whether the site is in a CDR
     */
    public record LiabilityView(
            String chain,
            LiabilityType type,
            int position,
            String motif,
            Region region,
            Risk risk,
            boolean inCdr) {

        static LiabilityView of(Liability l) {
            return new LiabilityView(
                    l.chainName(),
                    l.type(),
                    l.position(),
                    l.motif(),
                    l.region(),
                    l.risk(),
                    l.inCdr());
        }
    }

    /**
     * A peptide of the theoretical map.
     *
     * @param chain name of the chain
     * @param start 1-based position of its first residue
     * @param end 1-based position of its last residue
     * @param sequence its residues
     * @param missedCleavages number of missed cleavages
     * @param proForma the peptide with its fixed modifications
     * @param monoisotopicMass its neutral monoisotopic mass, in Da
     * @param regions regions it overlaps
     * @param coversCdr whether it overlaps a CDR
     * @param liabilities liabilities it contains
     * @param forms expected modified forms and their masses
     */
    public record PeptideView(
            String chain,
            int start,
            int end,
            String sequence,
            int missedCleavages,
            String proForma,
            double monoisotopicMass,
            List<Region> regions,
            boolean coversCdr,
            List<LiabilityView> liabilities,
            List<ModifiedForm> forms) {

        /** Creates the view of a peptide. */
        public static PeptideView of(MappedPeptide p) {
            return new PeptideView(
                    p.chainName(),
                    p.start(),
                    p.end(),
                    p.sequence(),
                    p.missedCleavages(),
                    p.proForma(),
                    p.monoisotopicMass(),
                    p.regions(),
                    p.coversCdr(),
                    p.liabilities().stream().map(LiabilityView::of).toList(),
                    p.modifiedForms());
        }
    }

    /**
     * Response of {@code POST /api/ms/import}.
     *
     * @param format format of the file
     * @param samples sample names, in order of appearance
     * @param peptides the observed peptides, to send back to {@code POST /api/quantify}
     * @param issues rows skipped, and why
     */
    public record ImportView(
            MsFormat format,
            List<String> samples,
            List<Observation> peptides,
            List<ParseIssue> issues) {

        /** Creates the view of an import. */
        public static ImportView of(MsImport imported) {
            return new ImportView(
                    imported.format(),
                    imported.samples(),
                    imported.peptides().stream()
                            .map(
                                    o ->
                                            new Observation(
                                                    o.peptide().toProForma(),
                                                    o.sample(),
                                                    o.intensity()))
                            .toList(),
                    imported.issues());
        }
    }

    /**
     * Response of {@code POST /api/quantify}.
     *
     * @param samples sample names
     * @param coverage coverage of each chain in each sample
     * @param sites modification levels of each site in each sample
     * @param unmatchedSequences observed sequences found in no chain
     * @param comparison lot comparison, or {@code null} without reference
     */
    public record QuantificationView(
            List<String> samples,
            List<CoverageView> coverage,
            List<SiteView> sites,
            List<String> unmatchedSequences,
            ComparisonView comparison) {

        /** Creates the view of a quantification and of its optional comparison. */
        public static QuantificationView of(Quantification q, ComparisonView comparison) {
            return new QuantificationView(
                    q.samples(),
                    q.coverage().stream().map(CoverageView::of).toList(),
                    q.sites().stream().map(SiteView::of).toList(),
                    q.unmatchedSequences(),
                    comparison);
        }
    }

    /**
     * Coverage of a chain in a sample.
     *
     * @param chain name of the chain
     * @param sample the sample
     * @param length residues of the chain
     * @param coveredResidues residues found in an observed peptide
     * @param percent percentage covered
     */
    public record CoverageView(
            String chain, String sample, int length, int coveredResidues, double percent) {

        static CoverageView of(ChainCoverage c) {
            return new CoverageView(
                    c.chainName(), c.sample(), c.length(), c.coveredResidues(), c.percent());
        }
    }

    /**
     * Modification of a site in a sample.
     *
     * @param site the liability
     * @param sample the sample
     * @param totalIntensity intensity of the peptides covering the site
     * @param percents percentage of each modified form
     * @param modifiedPercent percentage carrying any modified form
     */
    public record SiteView(
            LiabilityView site,
            String sample,
            double totalIntensity,
            Map<String, Double> percents,
            double modifiedPercent) {

        static SiteView of(SiteQuantity s) {
            Map<String, Double> percents = new LinkedHashMap<>();
            s.forms().keySet().forEach(form -> percents.put(form, s.percent(form)));
            return new SiteView(
                    LiabilityView.of(s.site()),
                    s.sample(),
                    s.totalIntensity(),
                    percents,
                    s.modifiedPercent());
        }
    }

    /**
     * Comparison of the lots with a reference.
     *
     * @param reference the reference sample
     * @param threshold comparability threshold, in percentage points
     * @param rows one comparison per form of each site
     */
    public record ComparisonView(
            String reference, double threshold, List<SiteComparisonView> rows) {

        /** Creates the view of a comparison. */
        public static ComparisonView of(
                String reference, double threshold, List<SiteComparison> rows) {
            return new ComparisonView(
                    reference, threshold, rows.stream().map(SiteComparisonView::of).toList());
        }
    }

    /**
     * One modified form of a site, compared between lots.
     *
     * @param site the liability
     * @param form name of the form
     * @param percents percentage in each sample
     * @param referencePercent percentage in the reference
     * @param maxDeviation largest difference with the reference, in percentage points
     * @param flagged whether the deviation exceeds the threshold
     */
    public record SiteComparisonView(
            LiabilityView site,
            String form,
            Map<String, Double> percents,
            double referencePercent,
            double maxDeviation,
            boolean flagged) {

        static SiteComparisonView of(SiteComparison c) {
            return new SiteComparisonView(
                    LiabilityView.of(c.site()),
                    c.form(),
                    c.percents(),
                    c.referencePercent(),
                    c.maxDeviation(),
                    c.flagged());
        }
    }

    /**
     * An example antibody offered by the interfaces.
     *
     * @param id identifier, such as {@code trastuzumab}
     * @param name display name
     * @param heavy heavy chain sequence
     * @param light light chain sequence
     */
    public record ExampleView(String id, String name, String heavy, String light) {}
}
