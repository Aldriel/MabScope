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
import com.bioinfo.mabscope.model.ChainType;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the modifications of the chain ends: N-terminal pyroglutamate, almost complete from a
 * glutamine and slow from a glutamate, and the heavy-chain C-terminal lysine, partly clipped during
 * production. Both make charge variants but rarely change the activity.
 */
public final class TerminalRule implements LiabilityRule {

    @Override
    public List<Liability> find(ChainAnnotation annotation) {
        Chain chain = annotation.chain();
        List<Liability> found = new ArrayList<>();
        char first = chain.residueAt(1);
        if (first == 'Q' || first == 'E') {
            found.add(
                    new Liability(
                            chain.name(),
                            LiabilityType.PYROGLUTAMATE,
                            1,
                            String.valueOf(first),
                            annotation.regionAt(1),
                            first == 'Q' ? Risk.MEDIUM : Risk.LOW));
        }
        int last = chain.length();
        if (chain.type() == ChainType.HEAVY && chain.residueAt(last) == 'K') {
            found.add(
                    new Liability(
                            chain.name(),
                            LiabilityType.C_TERMINAL_LYSINE,
                            last,
                            "K",
                            annotation.regionAt(last),
                            Risk.LOW));
        }
        return found;
    }
}
