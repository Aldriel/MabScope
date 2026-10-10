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

package com.bioinfo.mabscope.service;

import com.bioinfo.mabscope.liability.LiabilityScanner;
import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.mapping.PeptideMapper;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainAnnotation;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.ms.MsFormat;
import com.bioinfo.mabscope.ms.MsImport;
import com.bioinfo.mabscope.ms.MsResultImporter;
import com.bioinfo.mabscope.ms.ObservedPeptide;
import com.bioinfo.mabscope.quant.LotComparator;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.quant.SiteQuantifier;
import com.bioinfo.mabscope.region.CdrDetector;
import com.bioinfo.mabscope.report.PdfReportWriter;
import com.bioinfo.mabscope.report.QcReport;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Application facade (the "Model" of the MVC architecture) shared by the JavaFX desktop controllers
 * and the REST controllers consumed by the React front-end.
 *
 * <p>Every operation of the user interfaces goes through this class, so that both applications
 * compute exactly the same results. Instances are thread-safe.
 */
public final class AntibodyService {

    private final CdrDetector detector = new CdrDetector();
    private final MsResultImporter importer = new MsResultImporter();
    private final SiteQuantifier quantifier = new SiteQuantifier();
    private final PdfReportWriter reportWriter = new PdfReportWriter();
    private final LiabilityScanner scanner;

    /** Creates a service running every liability rule of MabScope. */
    public AntibodyService() {
        this(LiabilityScanner.withDefaultRules());
    }

    /**
     * Creates a service running the given liability rules.
     *
     * @param scanner finds the liabilities of each chain
     */
    public AntibodyService(LiabilityScanner scanner) {
        this.scanner = Objects.requireNonNull(scanner, "scanner");
    }

    /**
     * Locates the regions and the liabilities of every chain.
     *
     * @param antibody the antibody
     * @param scheme definition of the CDR boundaries
     * @return the analysis of each chain
     * @throws com.bioinfo.mabscope.region.RegionNotFoundException if a chain is not an antibody
     *     chain of its declared type
     */
    public AntibodyAnalysis analyze(Antibody antibody, NumberingScheme scheme) {
        Objects.requireNonNull(antibody, "antibody");
        List<ChainAnalysis> chains = new ArrayList<>();
        for (Chain chain : antibody.chains()) {
            ChainAnnotation annotation = detector.annotate(chain, scheme);
            chains.add(new ChainAnalysis(annotation, scanner.scan(annotation)));
        }
        return new AntibodyAnalysis(antibody, scheme, chains);
    }

    /**
     * Builds the theoretical peptide map of an antibody.
     *
     * @param analysis the antibody, its regions and liabilities
     * @param settings enzyme, peptide limits and alkylation
     * @return the peptides of every chain, chain after chain
     */
    public List<MappedPeptide> peptideMap(AntibodyAnalysis analysis, DigestionSettings settings) {
        Objects.requireNonNull(analysis, "analysis");
        PeptideMapper mapper = new PeptideMapper(settings);
        List<MappedPeptide> peptides = new ArrayList<>();
        for (ChainAnalysis chain : analysis.chains()) {
            peptides.addAll(mapper.map(chain.annotation(), chain.liabilities()));
        }
        return peptides;
    }

    /**
     * Imports the peptides measured by an MS software, detecting the format of the file.
     *
     * @param reader content of the result file; it is not closed
     * @return the observed peptides and the skipped rows
     * @throws IOException if the file cannot be read
     * @throws com.bioinfo.proteomics.parser.ParsingException if the format is not recognized
     */
    public MsImport importResults(Reader reader) throws IOException {
        return importer.read(reader);
    }

    /**
     * Imports the peptides measured by an MS software.
     *
     * @param reader content of the result file; it is not closed
     * @param format format of the file
     * @return the observed peptides and the skipped rows
     * @throws IOException if the file cannot be read
     * @throws com.bioinfo.proteomics.parser.ParsingException if a required column is missing
     */
    public MsImport importResults(Reader reader, MsFormat format) throws IOException {
        return importer.read(reader, format);
    }

    /**
     * Measures the coverage and the modification of each liability site.
     *
     * @param analysis the antibody, its regions and liabilities
     * @param observed the peptides imported from one or more samples
     * @return the quantities of each site in each sample
     */
    public Quantification quantify(AntibodyAnalysis analysis, List<ObservedPeptide> observed) {
        return quantifier.quantify(analysis, observed);
    }

    /**
     * Compares the modification levels of every sample with a reference lot.
     *
     * @param quantification the quantities of each site in each sample
     * @param reference name of the reference sample
     * @param threshold largest acceptable deviation, in percentage points
     * @return one comparison per form of each site measured in the reference
     */
    public List<SiteComparison> compareLots(
            Quantification quantification, String reference, double threshold) {
        return new LotComparator(threshold).compare(quantification, reference);
    }

    /**
     * Renders a quality control report as a PDF file.
     *
     * @param report the content of the report, assembled with {@link QcReport#builder}
     * @param output receives the PDF file; it is not closed
     * @throws IOException if the document cannot be produced or written
     */
    public void writeReport(QcReport report, OutputStream output) throws IOException {
        reportWriter.write(report, output);
    }
}
