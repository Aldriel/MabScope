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
import com.bioinfo.mabscope.server.api.Requests.AntibodyRequest;
import com.bioinfo.mabscope.server.api.Views.AnalysisView;
import com.bioinfo.mabscope.service.AntibodyService;

import java.io.IOException;

/**
 * Regions and liabilities of an antibody.
 *
 * <pre>
 * POST /api/analysis   {"name": "...", "heavy": "EVQL...", "light": "DIQM...", "scheme": "KABAT"}
 * </pre>
 *
 * <p>The response is an {@link AnalysisView}. A sequence that is not an antibody chain of its type
 * is answered with status 422.
 */
public final class AnalysisController extends JsonHandler {

    private final AntibodyService service;

    /**
     * Creates the controller.
     *
     * @param service model doing the analysis
     */
    public AnalysisController(AntibodyService service) {
        super("/api/analysis");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireRoute(request, "POST");
        AntibodyRequest body = request.json(AntibodyRequest.class);
        return AnalysisView.of(service.analyze(body.toAntibody(), body.schemeOrDefault()));
    }
}
