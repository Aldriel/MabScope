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

import com.bioinfo.mabscope.examples.ExampleAntibodies;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.server.HttpError;
import com.bioinfo.mabscope.server.JsonHandler;
import com.bioinfo.mabscope.server.api.Views.ExampleView;
import com.bioinfo.mabscope.server.api.Views.ImportView;
import com.bioinfo.mabscope.service.AntibodyService;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

/**
 * Examples for the demonstrations.
 *
 * <pre>
 * GET /api/examples                 the example antibodies, as {@link ExampleView}s
 * GET /api/examples/demo-results    simulated peptide mapping of three trastuzumab samples,
 *                                    as an {@link ImportView}
 * </pre>
 */
public final class ExampleController extends JsonHandler {

    private final AntibodyService service;

    /**
     * Creates the controller.
     *
     * @param service model reading the demonstration results
     */
    public ExampleController(AntibodyService service) {
        super("/api/examples");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        if (!request.method().equals("GET")) {
            throw new HttpError(405, "Method " + request.method() + " not allowed, use GET");
        }
        return switch (request.subPath()) {
            case "" ->
                    List.of(
                            view("trastuzumab", ExampleAntibodies.trastuzumab()),
                            view("adalimumab", ExampleAntibodies.adalimumab()));
            case "demo-results" ->
                    ImportView.of(
                            service.importResults(
                                    new StringReader(ExampleAntibodies.demoResults())));
            default -> throw new HttpError(404, "Unknown endpoint '" + request.subPath() + "'");
        };
    }

    private static ExampleView view(String id, Antibody antibody) {
        return new ExampleView(
                id,
                antibody.name(),
                antibody.chains().get(0).sequence(),
                antibody.chains().get(1).sequence());
    }
}
