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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares the modification levels of several lots (or stress conditions) with a reference lot,
 * such as the reference standard of a biosimilar or the unstressed control of a forced degradation
 * study. Only the sites measured in the reference are compared.
 */
public final class LotComparator {

    /** Default comparability threshold, in percentage points. */
    public static final double DEFAULT_THRESHOLD = 2.0;

    private final double threshold;

    /** Creates a comparator flagging the deviations above {@link #DEFAULT_THRESHOLD}. */
    public LotComparator() {
        this(DEFAULT_THRESHOLD);
    }

    /**
     * Creates a comparator.
     *
     * @param threshold largest acceptable deviation from the reference, in percentage points
     */
    public LotComparator(double threshold) {
        if (!(threshold >= 0) || Double.isInfinite(threshold)) {
            throw new IllegalArgumentException("The threshold must be a positive number");
        }
        this.threshold = threshold;
    }

    /**
     * Compares every sample with the reference.
     *
     * @param quantification the quantities measured in every sample
     * @param reference name of the reference sample
     * @return one comparison per form of each site measured in the reference
     * @throws IllegalArgumentException if the reference is not one of the samples
     */
    public List<SiteComparison> compare(Quantification quantification, String reference) {
        if (!quantification.samples().contains(reference)) {
            throw new IllegalArgumentException("Unknown reference sample '" + reference + "'");
        }
        Map<Liability, Map<String, SiteQuantity>> bySite = new LinkedHashMap<>();
        for (SiteQuantity q : quantification.sites()) {
            bySite.computeIfAbsent(q.site(), s -> new LinkedHashMap<>()).put(q.sample(), q);
        }
        List<SiteComparison> comparisons = new ArrayList<>();
        for (Map.Entry<Liability, Map<String, SiteQuantity>> site : bySite.entrySet()) {
            Map<String, SiteQuantity> bySample = site.getValue();
            SiteQuantity ref = bySample.get(reference);
            if (ref == null) {
                continue;
            }
            Set<String> forms = new LinkedHashSet<>(ref.forms().keySet());
            bySample.values().forEach(q -> forms.addAll(q.forms().keySet()));
            for (String form : forms) {
                comparisons.add(compare(site.getKey(), form, ref, quantification, bySample));
            }
        }
        return comparisons;
    }

    private SiteComparison compare(
            Liability site,
            String form,
            SiteQuantity ref,
            Quantification quantification,
            Map<String, SiteQuantity> bySample) {
        Map<String, Double> percents = new LinkedHashMap<>();
        double maxDeviation = 0;
        for (String sample : quantification.samples()) {
            SiteQuantity q = bySample.get(sample);
            if (q != null) {
                percents.put(sample, q.percent(form));
                maxDeviation =
                        Math.max(maxDeviation, Math.abs(q.percent(form) - ref.percent(form)));
            }
        }
        return new SiteComparison(
                site, form, percents, ref.percent(form), maxDeviation, maxDeviation > threshold);
    }
}
