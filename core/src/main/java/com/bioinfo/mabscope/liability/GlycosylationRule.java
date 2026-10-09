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

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the N-X-S/T sequons (X not proline). In the constant region it is the expected Fc glycan
 * (N297, EU numbering), a low risk; in the variable domain an unexpected glycan makes the product
 * heterogeneous and can block antigen binding, a high risk.
 */
public final class GlycosylationRule implements LiabilityRule {

    @Override
    public List<Liability> find(ChainAnnotation annotation) {
        Chain chain = annotation.chain();
        List<Liability> found = new ArrayList<>();
        for (int position = 1; position + 2 <= chain.length(); position++) {
            char next = chain.residueAt(position + 1);
            char third = chain.residueAt(position + 2);
            if (chain.residueAt(position) == 'N' && next != 'P' && (third == 'S' || third == 'T')) {
                Region region = annotation.regionAt(position);
                found.add(
                        new Liability(
                                chain.name(),
                                LiabilityType.N_GLYCOSYLATION,
                                position,
                                chain.subsequence(position, position + 2),
                                region,
                                region == Region.CONSTANT ? Risk.LOW : Risk.HIGH));
            }
        }
        return found;
    }
}
