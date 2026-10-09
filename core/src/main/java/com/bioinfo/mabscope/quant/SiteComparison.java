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

package com.bioinfo.mabscope.quant;

import com.bioinfo.mabscope.liability.Liability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One modified form of a site, compared between lots.
 *
 * @param site the liability
 * @param form name of the modified form
 * @param percents percentage of the form in each sample covering the site, in sample order
 * @param referencePercent percentage in the reference lot
 * @param maxDeviation largest absolute difference with the reference, in percentage points
 * @param flagged true when the deviation exceeds the comparability threshold
 */
public record SiteComparison(
        Liability site,
        String form,
        Map<String, Double> percents,
        double referencePercent,
        double maxDeviation,
        boolean flagged) {

    /** Validates the comparison and keeps the order of the samples. */
    public SiteComparison {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(form, "form");
        percents = Collections.unmodifiableMap(new LinkedHashMap<>(percents));
    }
}
