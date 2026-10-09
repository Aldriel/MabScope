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

package com.bioinfo.mabscope.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The regions of a chain under a numbering scheme.
 *
 * @param chain the annotated chain
 * @param scheme definition of the CDR boundaries
 * @param spans regions in order, covering every residue of the chain exactly once
 */
public record ChainAnnotation(Chain chain, NumberingScheme scheme, List<RegionSpan> spans) {

    /** Validates that the spans are ordered, contiguous and cover the whole chain. */
    public ChainAnnotation {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(scheme, "scheme");
        spans = List.copyOf(Objects.requireNonNull(spans, "spans"));
        int expectedStart = 1;
        for (RegionSpan span : spans) {
            if (span.start() != expectedStart) {
                throw new IllegalArgumentException(
                        span.region() + " starts at " + span.start() + ", not " + expectedStart);
            }
            expectedStart = span.end() + 1;
        }
        if (expectedStart != chain.length() + 1) {
            throw new IllegalArgumentException(
                    "Regions cover " + (expectedStart - 1) + " of " + chain.length() + " residues");
        }
    }

    /**
     * Finds the span of a region.
     *
     * @param region the region
     * @return its span; empty when the chain has no such region, such as a variable domain alone
     *     without constant region
     */
    public Optional<RegionSpan> span(Region region) {
        return spans.stream().filter(s -> s.region() == region).findFirst();
    }

    /**
     * Returns the residues of a region.
     *
     * @param region the region
     * @return its sequence; empty when the chain has no such region
     */
    public Optional<String> sequence(Region region) {
        return span(region).map(s -> chain.subsequence(s.start(), s.end()));
    }

    /**
     * Returns the region of a residue.
     *
     * @param position 1-based position in the chain
     * @return the region containing the residue
     * @throws IndexOutOfBoundsException if the position is not within the chain
     */
    public Region regionAt(int position) {
        Objects.checkIndex(position - 1, chain.length());
        return spans.stream().filter(s -> s.contains(position)).findFirst().orElseThrow().region();
    }
}
