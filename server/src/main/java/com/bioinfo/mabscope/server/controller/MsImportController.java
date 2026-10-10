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

import com.bioinfo.mabscope.ms.MsFormat;
import com.bioinfo.mabscope.ms.MsImport;
import com.bioinfo.mabscope.server.JsonHandler;
import com.bioinfo.mabscope.server.api.Views.ImportView;
import com.bioinfo.mabscope.service.AntibodyService;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Reads a result file of an MS software.
 *
 * <pre>
 * POST /api/ms/import?format=auto      body: the file (MaxQuant evidence.txt, FragPipe psm.tsv,
 *                                       Skyline report or generic CSV), encoded in UTF-8
 * </pre>
 *
 * <p>{@code format} is {@code auto} (default), {@code maxquant}, {@code fragpipe}, {@code skyline}
 * or {@code generic}. The response is an {@link ImportView}, whose peptides are then sent to {@code
 * POST /api/quantify}.
 */
public final class MsImportController extends JsonHandler {

    private final AntibodyService service;

    /**
     * Creates the controller.
     *
     * @param service model reading the files
     */
    public MsImportController(AntibodyService service) {
        super("/api/ms/import");
        this.service = service;
    }

    @Override
    protected Object handle(Request request) throws IOException {
        requireRoute(request, "POST");
        Reader file = new InputStreamReader(request.upload(), StandardCharsets.UTF_8);
        MsImport imported =
                "auto".equalsIgnoreCase(request.param("format", "auto"))
                        ? service.importResults(file)
                        : service.importResults(
                                file, request.enumParam("format", MsFormat.class, null));
        return ImportView.of(imported);
    }
}
