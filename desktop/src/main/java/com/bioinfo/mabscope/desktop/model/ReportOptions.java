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

package com.bioinfo.mabscope.desktop.model;

import java.util.Locale;
import java.util.Objects;

/**
 * Content of the quality control report chosen in the Report tab.
 *
 * @param locale language of the report; its country also chooses the paper size
 * @param analyst name of the analyst; blank to omit it
 * @param notes free-text notes; blank to omit them
 * @param includePeptideMap whether to include the theoretical peptide map
 * @param includeQuantification whether to include the LC-MS results and the lot comparison, when
 *     results were imported
 */
public record ReportOptions(
        Locale locale,
        String analyst,
        String notes,
        boolean includePeptideMap,
        boolean includeQuantification) {

    /** Validates the options. */
    public ReportOptions {
        Objects.requireNonNull(locale, "locale");
    }
}
