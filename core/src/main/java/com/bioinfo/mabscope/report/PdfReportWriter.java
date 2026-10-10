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

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.mapping.MappedPeptide;
import com.bioinfo.mabscope.model.ChainAnnotation;
import com.bioinfo.mabscope.model.RegionSpan;
import com.bioinfo.mabscope.quant.ChainCoverage;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.quant.SiteQuantity;
import com.bioinfo.mabscope.report.PdfCanvas.TableRow;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.ChainAnalysis;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Renders a {@link QcReport} as a PDF document: identification of the antibody, chains and CDRs,
 * liabilities, then the optional peptide map, quantification, lot comparison and notes. Every page
 * carries the signature of the author, its number, a disclaimer and the provenance.
 *
 * <p>Instances are stateless and thread-safe.
 */
public final class PdfReportWriter {

    /** Name of the application, in the title and the footer. */
    public static final String APP_NAME = "MabScope";

    /** Contact e-mail of the author, in the footer. */
    public static final String CONTACT_EMAIL = "contact@maximeethier.com";

    private static final int MAX_UNMATCHED_LISTED = 10;
    private static final float LABEL_WIDTH = 120;

    /**
     * Renders a report in memory, such as for an HTTP response.
     *
     * @param report the report
     * @return the PDF file
     * @throws IOException if the document cannot be produced
     */
    public byte[] toBytes(QcReport report) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        write(report, bytes);
        return bytes.toByteArray();
    }

    /**
     * Renders a report.
     *
     * @param report the report
     * @param output receives the PDF file; it is not closed
     * @throws IOException if the document cannot be produced or written
     */
    public void write(QcReport report, OutputStream output) throws IOException {
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(output, "output");
        ReportTexts texts = new ReportTexts(report.locale(), report.zone());
        try (PDDocument document = new PDDocument()) {
            metadata(document, report, texts);
            PdfCanvas canvas = new PdfCanvas(document, pageSizeFor(report.locale()));
            canvas.newPage();
            try {
                new Sections(canvas, texts).render(report);
            } finally {
                canvas.finish();
            }
            String provenance =
                    texts.get(
                            "footer.generated",
                            texts.dateTime(report.generatedAt()),
                            APP_NAME,
                            report.appVersion());
            canvas.footers(texts, provenance, CONTACT_EMAIL);
            document.save(output);
        }
    }

    /**
     * Returns the usual paper size of a locale: US Letter in the United States and Canada, A4
     * elsewhere.
     *
     * @param locale locale of the reader
     * @return the page size
     */
    public static PDRectangle pageSizeFor(Locale locale) {
        String country = locale.getCountry();
        return "US".equals(country) || "CA".equals(country) ? PDRectangle.LETTER : PDRectangle.A4;
    }

    private static void metadata(PDDocument document, QcReport report, ReportTexts texts) {
        PDDocumentInformation info = document.getDocumentInformation();
        info.setTitle(report.analysis().antibody().name() + " - " + texts.get("report.title"));
        info.setAuthor(report.analyst().orElse(texts.get("brand.title")));
        info.setCreator(APP_NAME + " " + report.appVersion());
        info.setKeywords(texts.get("brand.website") + ", " + CONTACT_EMAIL);
        Calendar created = GregorianCalendar.getInstance();
        created.setTimeInMillis(report.generatedAt().toEpochMilli());
        info.setCreationDate(created);
    }

    /** Writes the sections of one report. */
    private static final class Sections {

        private final PdfCanvas canvas;
        private final ReportTexts texts;

        Sections(PdfCanvas canvas, ReportTexts texts) {
            this.canvas = canvas;
            this.texts = texts;
        }

        void render(QcReport report) throws IOException {
            header(report);
            chains(report.analysis());
            liabilities(report.analysis());
            if (report.peptideMap().isPresent()) {
                peptideMap(report.peptideMap().get());
            }
            if (report.quantification().isPresent()) {
                quantification(report.quantification().get());
            }
            if (report.comparison().isPresent()) {
                comparison(report.comparison().get());
            }
            if (report.notes().isPresent()) {
                canvas.sectionTitle(texts.get("section.notes"));
                for (String paragraph : report.notes().get().split("\\R")) {
                    canvas.paragraph(
                            paragraph, canvas.regular, PdfCanvas.BODY_SIZE, PdfCanvas.TEXT);
                }
            }
        }

        private void header(QcReport report) throws IOException {
            AntibodyAnalysis analysis = report.analysis();
            canvas.paragraph(
                    APP_NAME + "  ·  " + texts.get("report.title"),
                    canvas.bold,
                    PdfCanvas.SMALL_SIZE + 1,
                    PdfCanvas.ACCENT);
            canvas.gap(8);
            canvas.title(analysis.antibody().name(), 22);
            canvas.gap(6);
            canvas.detailRow(
                    texts.get("header.scheme"), texts.of("scheme", analysis.scheme()), LABEL_WIDTH);
            String chains =
                    analysis.chains().stream()
                            .map(
                                    c ->
                                            texts.get(
                                                    "header.chainSummary",
                                                    c.chain().name(),
                                                    c.chain().length()))
                            .collect(Collectors.joining(", "));
            canvas.detailRow(texts.get("header.chains"), chains, LABEL_WIDTH);
            if (report.analyst().isPresent()) {
                canvas.detailRow(texts.get("header.analyst"), report.analyst().get(), LABEL_WIDTH);
            }
            canvas.detailRow(
                    texts.get("header.generated"),
                    texts.dateTime(report.generatedAt()),
                    LABEL_WIDTH);
        }

        private void chains(AntibodyAnalysis analysis) throws IOException {
            canvas.sectionTitle(texts.get("section.chains"));
            caption(texts.get("chains.caption", texts.of("scheme", analysis.scheme())));
            List<TableRow> rows = new ArrayList<>();
            for (ChainAnalysis chain : analysis.chains()) {
                ChainAnnotation annotation = chain.annotation();
                for (RegionSpan span : annotation.spans()) {
                    if (span.region().isCdr()) {
                        rows.add(
                                TableRow.of(
                                        List.of(
                                                chain.chain().name(),
                                                span.region().name(),
                                                span.start() + "-" + span.end(),
                                                String.valueOf(span.length()),
                                                annotation.sequence(span.region()).orElseThrow())));
                    }
                }
            }
            canvas.table(
                    List.of(
                            texts.get("table.chain"),
                            texts.get("table.region"),
                            texts.get("table.positions"),
                            texts.get("table.length"),
                            texts.get("table.sequence")),
                    new float[] {0.2f, 0.1f, 0.14f, 0.1f, 0.46f},
                    rows);
        }

        private void liabilities(AntibodyAnalysis analysis) throws IOException {
            canvas.sectionTitle(texts.get("section.liabilities"));
            List<Liability> all = analysis.liabilities();
            if (all.isEmpty()) {
                caption(texts.get("liabilities.none"));
                return;
            }
            Map<Risk, Long> byRisk =
                    all.stream()
                            .collect(Collectors.groupingBy(Liability::risk, Collectors.counting()));
            canvas.paragraph(
                    texts.get(
                            "liabilities.summary",
                            all.size(),
                            byRisk.getOrDefault(Risk.HIGH, 0L),
                            byRisk.getOrDefault(Risk.MEDIUM, 0L),
                            byRisk.getOrDefault(Risk.LOW, 0L),
                            all.stream().filter(Liability::inCdr).count()),
                    canvas.bold,
                    PdfCanvas.BODY_SIZE,
                    PdfCanvas.TEXT);
            caption(texts.get("liabilities.caption"));
            List<TableRow> rows = new ArrayList<>();
            for (Liability l : all) {
                rows.add(
                        new TableRow(
                                List.of(
                                        l.chainName(),
                                        residue(l),
                                        texts.of("type", l.type()),
                                        l.motif(),
                                        l.region().name(),
                                        texts.of("risk", l.risk())),
                                5,
                                color(l.risk())));
            }
            canvas.table(
                    List.of(
                            texts.get("table.chain"),
                            texts.get("table.position"),
                            texts.get("table.type"),
                            texts.get("table.motif"),
                            texts.get("table.region"),
                            texts.get("table.risk")),
                    new float[] {0.2f, 0.12f, 0.26f, 0.12f, 0.12f, 0.18f},
                    rows);
        }

        private void peptideMap(QcReport.PeptideMapSection section) throws IOException {
            canvas.sectionTitle(texts.get("section.peptideMap"));
            DigestionSettings s = section.settings();
            caption(
                    texts.get(
                            "peptideMap.caption",
                            texts.of("protease", s.protease()),
                            s.missedCleavages(),
                            s.minLength(),
                            s.maxLength(),
                            texts.get(
                                    s.alkylatedCysteines()
                                            ? "peptideMap.alkylated"
                                            : "peptideMap.notAlkylated")));
            List<TableRow> rows = new ArrayList<>();
            for (MappedPeptide p : section.peptides()) {
                rows.add(
                        TableRow.of(
                                List.of(
                                        p.chainName(),
                                        p.start() + "-" + p.end(),
                                        p.sequence(),
                                        texts.mass(p.monoisotopicMass()),
                                        p.liabilities().stream()
                                                .map(
                                                        l ->
                                                                residue(l)
                                                                        + " "
                                                                        + texts.of(
                                                                                "type", l.type()))
                                                .collect(Collectors.joining(", ")))));
            }
            canvas.table(
                    List.of(
                            texts.get("table.chain"),
                            texts.get("table.positions"),
                            texts.get("table.peptide"),
                            texts.get("table.mass"),
                            texts.get("table.liabilities")),
                    new float[] {0.15f, 0.11f, 0.36f, 0.12f, 0.26f},
                    rows);
        }

        private void quantification(Quantification q) throws IOException {
            canvas.sectionTitle(texts.get("section.coverage"));
            List<TableRow> coverage = new ArrayList<>();
            for (ChainCoverage c : q.coverage()) {
                coverage.add(
                        TableRow.of(
                                List.of(
                                        c.sample(),
                                        c.chainName(),
                                        texts.percent(c.percent())
                                                + " ("
                                                + c.coveredResidues()
                                                + "/"
                                                + c.length()
                                                + ")")));
            }
            canvas.table(
                    List.of(
                            texts.get("table.sample"),
                            texts.get("table.chain"),
                            texts.get("table.coverage")),
                    new float[] {0.4f, 0.3f, 0.3f},
                    coverage);
            if (!q.unmatchedSequences().isEmpty()) {
                List<String> listed =
                        q.unmatchedSequences().stream().limit(MAX_UNMATCHED_LISTED).toList();
                String more = q.unmatchedSequences().size() > MAX_UNMATCHED_LISTED ? ", …" : "";
                caption(
                        texts.get(
                                "quant.unmatched",
                                q.unmatchedSequences().size(),
                                String.join(", ", listed) + more));
            }

            canvas.sectionTitle(texts.get("section.sites"));
            if (q.sites().isEmpty()) {
                caption(texts.get("sites.none"));
                return;
            }
            caption(texts.get("sites.caption"));
            List<TableRow> rows = new ArrayList<>();
            for (SiteQuantity s : q.sites()) {
                for (String form : s.forms().keySet()) {
                    rows.add(
                            TableRow.of(
                                    List.of(
                                            site(s.site()),
                                            texts.of("type", s.site().type()),
                                            s.sample(),
                                            form,
                                            texts.percent(s.percent(form)))));
                }
            }
            canvas.table(
                    List.of(
                            texts.get("table.site"),
                            texts.get("table.type"),
                            texts.get("table.sample"),
                            texts.get("table.form"),
                            texts.get("table.modified")),
                    new float[] {0.22f, 0.2f, 0.2f, 0.2f, 0.18f},
                    rows);
        }

        private void comparison(QcReport.ComparisonSection section) throws IOException {
            canvas.sectionTitle(texts.get("section.comparison"));
            caption(
                    texts.get(
                            "comparison.caption",
                            section.reference(),
                            String.format(texts.locale(), "%.1f", section.threshold())));
            if (section.rows().isEmpty()) {
                caption(texts.get("comparison.none"));
                return;
            }
            List<TableRow> rows = new ArrayList<>();
            for (SiteComparison c : section.rows()) {
                String lots =
                        c.percents().entrySet().stream()
                                .filter(e -> !e.getKey().equals(section.reference()))
                                .map(e -> e.getKey() + ": " + texts.percent(e.getValue()))
                                .collect(Collectors.joining(", "));
                rows.add(
                        new TableRow(
                                List.of(
                                        site(c.site()),
                                        c.form(),
                                        texts.percent(c.referencePercent()),
                                        lots,
                                        texts.points(c.maxDeviation()),
                                        texts.get(
                                                c.flagged()
                                                        ? "status.flagged"
                                                        : "status.comparable")),
                                5,
                                c.flagged() ? PdfCanvas.HIGH : PdfCanvas.LOW));
            }
            canvas.table(
                    List.of(
                            texts.get("table.site"),
                            texts.get("table.form"),
                            texts.get("table.reference"),
                            texts.get("table.sample"),
                            texts.get("table.deviation"),
                            texts.get("table.status")),
                    new float[] {0.2f, 0.14f, 0.12f, 0.24f, 0.12f, 0.18f},
                    rows);
        }

        private void caption(String text) throws IOException {
            canvas.paragraph(text, canvas.regular, PdfCanvas.SMALL_SIZE + 0.5f, PdfCanvas.MUTED);
            canvas.gap(4);
        }

        /** Names a site by its residue and position, such as {@code N55}. */
        private static String residue(Liability l) {
            return l.motif().charAt(0) + String.valueOf(l.position());
        }

        private static String site(Liability l) {
            return l.chainName() + " " + residue(l);
        }

        private static float[] color(Risk risk) {
            return switch (risk) {
                case HIGH -> PdfCanvas.HIGH;
                case MEDIUM -> PdfCanvas.MEDIUM;
                case LOW -> PdfCanvas.LOW;
            };
        }
    }
}
