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

package com.bioinfo.mabscope.service;

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainAnnotation;

import java.util.List;
import java.util.Objects;

/**
 * The regions and liabilities of one chain.
 *
 * @param annotation the chain and its regions
 * @param liabilities its liabilities, by position
 */
public record ChainAnalysis(ChainAnnotation annotation, List<Liability> liabilities) {

    /** Validates the analysis and copies its liabilities, so that it is immutable. */
    public ChainAnalysis {
        Objects.requireNonNull(annotation, "annotation");
        liabilities = List.copyOf(liabilities);
    }

    /** Returns the analyzed chain. */
    public Chain chain() {
        return annotation.chain();
    }
}
