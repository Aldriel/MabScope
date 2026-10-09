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

import java.util.List;

/**
 * Finds one kind of liability in a chain (Strategy pattern): {@link LiabilityScanner} runs every
 * rule, so a new kind of liability is added by writing a new rule.
 */
public interface LiabilityRule {

    /**
     * Finds the sites of the chain.
     *
     * @param annotation the chain and its regions
     * @return the sites with their intrinsic risk, before any adjustment for CDRs
     */
    List<Liability> find(ChainAnnotation annotation);
}
