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

package com.bioinfo.mabscope.server.controller;

import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.ms.ModificationCatalog;
import com.bioinfo.mabscope.ms.ObservedPeptide;
import com.bioinfo.mabscope.quant.LotComparator;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.report.QcReport;
import com.bioinfo.mabscope.server.HttpError;
import com.bioinfo.mabscope.server.JsonHandler;
import com.bioinfo.mabscope.server.api.Requests;
import com.bioinfo.mabscope.server.api.Requests.AntibodyRequest;
import com.bioinfo.mabscope.server.api.Requests.ReportRequest;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Quality control report of an antibody, as a PDF file.
 *
 * <pre>
 * POST /api/report   {"antibody": {...}, "locale": "fr-CA", "timeZone": "America/Toronto",
 *                     "analyst": "...", "notes": "...", "digestion": {...},
 *                     "peptides": [...], "reference": "Lot A", "threshold": 2}
 * </pre>
 *
 * <p>Only {@code antibody} is required; each optional section of the report is included when its
 * fields are given. The response is the PDF file, as an attachment.
 */
public final class ReportController extends JsonHandler {

    private final AntibodyService service;
    private final String appVersion;
    private final PeptideSequenceParser parser =
            new PeptideSequenceParser(new ModificationCatalog().registry());

    /**
     * Creates the controller.
     *
     * @param service model building the report
     * @param appVersion version of the application, printed in the report
     */
    public ReportController(AntibodyService service, String appVersion) {
        super("/api/report");
        this.service = service;
        this.appVersion = appVersion;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireRoute(request, "POST");
        ReportRequest body = request.json(ReportRequest.class);
        AntibodyRequest antibody = Requests.required(body.antibody(), "antibody");
        AntibodyAnalysis analysis =
                service.analyze(antibody.toAntibody(), antibody.schemeOrDefault());
        QcReport.Builder report =
                QcReport.builder(analysis)
                        .locale(locale(body.locale()))
                        .zone(zone(body.timeZone()))
                        .generatedAt(Instant.now())
                        .appVersion(appVersion)
                        .analyst(body.analyst())
                        .notes(body.notes());
        if (body.digestion() != null) {
            DigestionSettings settings = body.digestion().toSettings();
            report.peptideMap(settings, service.peptideMap(analysis, settings));
        }
        List<ObservedPeptide> observed = body.observed(parser);
        if (!observed.isEmpty()) {
            Quantification q = service.quantify(analysis, observed);
            report.quantification(q);
            if (body.reference() != null && !body.reference().isBlank()) {
                double threshold =
                        Objects.requireNonNullElse(
                                body.threshold(), LotComparator.DEFAULT_THRESHOLD);
                report.comparison(
                        body.reference(),
                        threshold,
                        service.compareLots(q, body.reference(), threshold));
            }
        }
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        service.writeReport(report.build(), pdf);
        return new FileResponse(
                pdf.toByteArray(),
                "application/pdf",
                QcReport.fileName(analysis.antibody().name()));
    }

    private static Locale locale(String tag) {
        return tag == null || tag.isBlank() ? Locale.ENGLISH : Locale.forLanguageTag(tag.strip());
    }

    private static ZoneId zone(String id) {
        if (id == null || id.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(id.strip());
        } catch (DateTimeException e) {
            throw new HttpError(400, "Unknown time zone '" + id + "'");
        }
    }
}
