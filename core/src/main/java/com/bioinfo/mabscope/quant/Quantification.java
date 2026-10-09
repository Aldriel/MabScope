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

import java.util.List;

/**
 * The modifications measured in a set of samples.
 *
 * @param samples sample names, in order of first appearance in the data
 * @param coverage coverage of each chain in each sample
 * @param sites quantity of each liability site covered in each sample, site after site
 * @param unmatchedSequences observed sequences found in no chain, such as contaminants
 */
public record Quantification(
        List<String> samples,
        List<ChainCoverage> coverage,
        List<SiteQuantity> sites,
        List<String> unmatchedSequences) {

    /** Copies the lists. */
    public Quantification {
        samples = List.copyOf(samples);
        coverage = List.copyOf(coverage);
        sites = List.copyOf(sites);
        unmatchedSequences = List.copyOf(unmatchedSequences);
    }
}
