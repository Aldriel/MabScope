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

import java.util.Objects;

/**
 * The residues of a chain that belong to a region.
 *
 * @param region the region
 * @param start 1-based position of the first residue
 * @param end 1-based position of the last residue, inclusive
 */
public record RegionSpan(Region region, int start, int end) {

    /** Validates the span: {@code 1 <= start <= end}. */
    public RegionSpan {
        Objects.requireNonNull(region, "region");
        if (start < 1 || end < start) {
            throw new IllegalArgumentException(
                    "Invalid span of " + region + ": " + start + ".." + end);
        }
    }

    /** Returns the number of residues. */
    public int length() {
        return end - start + 1;
    }

    /**
     * Tells whether a residue belongs to the span.
     *
     * @param position 1-based position in the chain
     * @return true when {@code start <= position <= end}
     */
    public boolean contains(int position) {
        return position >= start && position <= end;
    }
}
