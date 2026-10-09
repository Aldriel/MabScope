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
 * How much of a liability site is modified in a sample: the intensity of the peptides carrying each
 * modified form, relative to the intensity of all the peptides covering the site.
 *
 * @param site the liability
 * @param sample the sample
 * @param totalIntensity summed intensity of the peptides covering the site, strictly positive
 * @param forms summed intensity of each modified form, such as {@code Deamidated} or {@code G0F}
 */
public record SiteQuantity(
        Liability site, String sample, double totalIntensity, Map<String, Double> forms) {

    /** Validates the quantity and keeps the order of the forms. */
    public SiteQuantity {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(sample, "sample");
        if (!(totalIntensity > 0)) {
            throw new IllegalArgumentException("The total intensity must be positive");
        }
        forms = Collections.unmodifiableMap(new LinkedHashMap<>(forms));
    }

    /**
     * Returns the share of a modified form.
     *
     * @param form name of the form
     * @return its percentage of the site, 0 when it was not observed
     */
    public double percent(String form) {
        return 100 * forms.getOrDefault(form, 0.0) / totalIntensity;
    }

    /** Returns the percentage of the site carrying any modified form. */
    public double modifiedPercent() {
        return 100
                * forms.values().stream().mapToDouble(Double::doubleValue).sum()
                / totalIntensity;
    }
}
