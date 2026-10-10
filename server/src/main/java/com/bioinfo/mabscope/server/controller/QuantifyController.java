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

import com.bioinfo.mabscope.ms.ModificationCatalog;
import com.bioinfo.mabscope.quant.LotComparator;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.server.JsonHandler;
import com.bioinfo.mabscope.server.api.Requests;
import com.bioinfo.mabscope.server.api.Requests.AntibodyRequest;
import com.bioinfo.mabscope.server.api.Requests.QuantifyRequest;
import com.bioinfo.mabscope.server.api.Views.ComparisonView;
import com.bioinfo.mabscope.server.api.Views.QuantificationView;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;
import com.bioinfo.proteomics.parser.PeptideSequenceParser;

import java.io.IOException;
import java.util.Objects;

/**
 * Coverage and modification levels measured in one or more samples, and the lot comparison.
 *
 * <pre>
 * POST /api/quantify   {"antibody": {...}, "peptides": [{"sequence": "IYPTN[Deamidated]GYTR",
 *                       "sample": "Lot A", "intensity": 1.2e6}, ...],
 *                       "reference": "Lot A", "threshold": 2}
 * </pre>
 *
 * <p>Without {@code reference}, the response has no comparison. The response is a {@link
 * QuantificationView}.
 */
public final class QuantifyController extends JsonHandler {

    private final AntibodyService service;
    private final PeptideSequenceParser parser =
            new PeptideSequenceParser(new ModificationCatalog().registry());

    /**
     * Creates the controller.
     *
     * @param service model doing the quantification
     */
    public QuantifyController(AntibodyService service) {
        super("/api/quantify");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireRoute(request, "POST");
        QuantifyRequest body = request.json(QuantifyRequest.class);
        AntibodyRequest antibody = Requests.required(body.antibody(), "antibody");
        AntibodyAnalysis analysis =
                service.analyze(antibody.toAntibody(), antibody.schemeOrDefault());
        Quantification q = service.quantify(analysis, body.observed(parser));
        ComparisonView comparison = null;
        if (body.reference() != null && !body.reference().isBlank()) {
            double threshold =
                    Objects.requireNonNullElse(body.threshold(), LotComparator.DEFAULT_THRESHOLD);
            comparison =
                    ComparisonView.of(
                            body.reference(),
                            threshold,
                            service.compareLots(q, body.reference(), threshold));
        }
        return QuantificationView.of(q, comparison);
    }
}
