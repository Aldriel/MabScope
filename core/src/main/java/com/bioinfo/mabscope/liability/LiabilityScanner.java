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

import com.bioinfo.mabscope.model.ChainAnnotation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Runs a set of {@link LiabilityRule}s on a chain. A site in a CDR is one risk level higher than
 * the rule says, since a change there can alter antigen binding.
 */
public final class LiabilityScanner {

    private final List<LiabilityRule> rules;

    /**
     * Creates a scanner.
     *
     * @param rules the rules to run
     */
    public LiabilityScanner(List<LiabilityRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    /** Creates a scanner running every rule of MabScope. */
    public static LiabilityScanner withDefaultRules() {
        return new LiabilityScanner(
                List.of(
                        DipeptideRule.deamidation(),
                        DipeptideRule.isomerization(),
                        DipeptideRule.aspProCleavage(),
                        new OxidationRule(),
                        new GlycosylationRule(),
                        new UnpairedCysteineRule(),
                        new TerminalRule()));
    }

    /**
     * Finds the liabilities of a chain.
     *
     * @param annotation the chain and its regions
     * @return the liabilities, by position, then by kind
     */
    public List<Liability> scan(ChainAnnotation annotation) {
        Objects.requireNonNull(annotation, "annotation");
        List<Liability> found = new ArrayList<>();
        for (LiabilityRule rule : rules) {
            for (Liability l : rule.find(annotation)) {
                found.add(l.inCdr() ? withRisk(l, l.risk().raised()) : l);
            }
        }
        found.sort(Comparator.comparingInt(Liability::position).thenComparing(Liability::type));
        return List.copyOf(found);
    }

    private static Liability withRisk(Liability l, Risk risk) {
        return new Liability(l.chainName(), l.type(), l.position(), l.motif(), l.region(), risk);
    }
}
