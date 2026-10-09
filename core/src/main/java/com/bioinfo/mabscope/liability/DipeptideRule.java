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
import java.util.Map;
import java.util.Objects;

/**
 * Finds a residue whose reactivity depends on the residue that follows it: the smaller and more
 * flexible the next residue, the faster the reaction.
 */
public final class DipeptideRule implements LiabilityRule {

    private final LiabilityType type;
    private final char residue;
    private final Map<Character, Risk> riskByNextResidue;

    /**
     * Creates a rule.
     *
     * @param type kind of liability
     * @param residue the modified residue
     * @param riskByNextResidue risk for each residue that can follow; other residues are ignored
     */
    public DipeptideRule(LiabilityType type, char residue, Map<Character, Risk> riskByNextResidue) {
        this.type = Objects.requireNonNull(type, "type");
        this.residue = residue;
        this.riskByNextResidue = Map.copyOf(riskByNextResidue);
    }

    /** Deamidation of asparagine: NG fastest, then NS; NT, NN and NH slower. */
    public static DipeptideRule deamidation() {
        return new DipeptideRule(
                LiabilityType.DEAMIDATION,
                'N',
                Map.of(
                        'G', Risk.HIGH,
                        'S', Risk.MEDIUM,
                        'T', Risk.LOW,
                        'N', Risk.LOW,
                        'H', Risk.LOW));
    }

    /** Isomerization of aspartate: DG fastest, then DS; DT, DD and DH slower. */
    public static DipeptideRule isomerization() {
        return new DipeptideRule(
                LiabilityType.ISOMERIZATION,
                'D',
                Map.of(
                        'G', Risk.HIGH,
                        'S', Risk.MEDIUM,
                        'T', Risk.LOW,
                        'D', Risk.LOW,
                        'H', Risk.LOW));
    }

    /** Cleavage of the acid-labile Asp-Pro bond. */
    public static DipeptideRule aspProCleavage() {
        return new DipeptideRule(LiabilityType.ASP_PRO_CLEAVAGE, 'D', Map.of('P', Risk.MEDIUM));
    }

    @Override
    public List<Liability> find(ChainAnnotation annotation) {
        Chain chain = annotation.chain();
        List<Liability> found = new ArrayList<>();
        for (int position = 1; position < chain.length(); position++) {
            if (chain.residueAt(position) != residue) {
                continue;
            }
            Risk risk = riskByNextResidue.get(chain.residueAt(position + 1));
            if (risk != null) {
                found.add(
                        new Liability(
                                chain.name(),
                                type,
                                position,
                                chain.subsequence(position, position + 1),
                                annotation.regionAt(position),
                                risk));
            }
        }
        return found;
    }
}
