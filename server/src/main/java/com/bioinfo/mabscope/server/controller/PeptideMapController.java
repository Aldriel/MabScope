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

import com.bioinfo.mabscope.server.JsonHandler;
import com.bioinfo.mabscope.server.api.Requests;
import com.bioinfo.mabscope.server.api.Requests.AntibodyRequest;
import com.bioinfo.mabscope.server.api.Requests.PeptideMapRequest;
import com.bioinfo.mabscope.server.api.Views.PeptideView;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;

import java.io.IOException;

/**
 * Theoretical peptide map of an antibody.
 *
 * <pre>
 * POST /api/peptide-map   {"antibody": {...}, "digestion": {"protease": "TRYPSIN",
 *                          "missedCleavages": 1, "minLength": 1, "maxLength": 60,
 *                          "alkylatedCysteines": true}}
 * </pre>
 *
 * <p>Every digestion field is optional. The response is a list of {@link PeptideView}s, chain after
 * chain.
 */
public final class PeptideMapController extends JsonHandler {

    private final AntibodyService service;

    /**
     * Creates the controller.
     *
     * @param service model doing the digestion
     */
    public PeptideMapController(AntibodyService service) {
        super("/api/peptide-map");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireRoute(request, "POST");
        PeptideMapRequest body = request.json(PeptideMapRequest.class);
        AntibodyRequest antibody = Requests.required(body.antibody(), "antibody");
        AntibodyAnalysis analysis =
                service.analyze(antibody.toAntibody(), antibody.schemeOrDefault());
        return service.peptideMap(analysis, body.settings()).stream().map(PeptideView::of).toList();
    }
}
