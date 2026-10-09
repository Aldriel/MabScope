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

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the methionines and tryptophans. Methionines oxidize readily, such as M252 and M428 in the
 * Fc, which weakens FcRn binding; tryptophans mainly when exposed, as in a CDR.
 */
public final class OxidationRule implements LiabilityRule {

    @Override
    public List<Liability> find(ChainAnnotation annotation) {
        Chain chain = annotation.chain();
        List<Liability> found = new ArrayList<>();
        for (int position = 1; position <= chain.length(); position++) {
            char c = chain.residueAt(position);
            if (c == 'M' || c == 'W') {
                found.add(
                        new Liability(
                                chain.name(),
                                LiabilityType.OXIDATION,
                                position,
                                String.valueOf(c),
                                annotation.regionAt(position),
                                c == 'M' ? Risk.MEDIUM : Risk.LOW));
            }
        }
        return found;
    }
}
