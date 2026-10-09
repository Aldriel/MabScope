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

package com.bioinfo.mabscope.liability;

import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainAnnotation;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.model.RegionSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Finds the cysteines of the variable domain other than the two that form its conserved disulfide
 * bond: the last cysteine of FR1 and the last one of FR3, which frame CDR1 and CDR3. An unpaired
 * cysteine can form wrong disulfide bonds or aggregates.
 */
public final class UnpairedCysteineRule implements LiabilityRule {

    private static final Set<Region> VARIABLE_DOMAIN =
            Set.of(
                    Region.FR1,
                    Region.CDR1,
                    Region.FR2,
                    Region.CDR2,
                    Region.FR3,
                    Region.CDR3,
                    Region.FR4);

    @Override
    public List<Liability> find(ChainAnnotation annotation) {
        Chain chain = annotation.chain();
        int fr1Cysteine = lastCysteine(annotation, Region.FR1);
        int fr3Cysteine = lastCysteine(annotation, Region.FR3);
        List<Liability> found = new ArrayList<>();
        for (RegionSpan span : annotation.spans()) {
            if (!VARIABLE_DOMAIN.contains(span.region())) {
                continue;
            }
            for (int position = span.start(); position <= span.end(); position++) {
                if (chain.residueAt(position) == 'C'
                        && position != fr1Cysteine
                        && position != fr3Cysteine) {
                    found.add(
                            new Liability(
                                    chain.name(),
                                    LiabilityType.UNPAIRED_CYSTEINE,
                                    position,
                                    "C",
                                    span.region(),
                                    Risk.HIGH));
                }
            }
        }
        return found;
    }

    /** Returns the position of the last cysteine of a region, or 0 when it has none. */
    private static int lastCysteine(ChainAnnotation annotation, Region region) {
        RegionSpan span = annotation.span(region).orElseThrow();
        for (int position = span.end(); position >= span.start(); position--) {
            if (annotation.chain().residueAt(position) == 'C') {
                return position;
            }
        }
        return 0;
    }
}
