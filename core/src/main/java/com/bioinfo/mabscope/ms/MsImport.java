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

package com.bioinfo.mabscope.ms;

import com.bioinfo.proteomics.parser.ParseIssue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * The content of an imported result file.
 *
 * @param format format of the file
 * @param peptides the observed peptides, in file order
 * @param issues rows that were skipped, and why
 */
public record MsImport(MsFormat format, List<ObservedPeptide> peptides, List<ParseIssue> issues) {

    /** Copies the lists. */
    public MsImport {
        Objects.requireNonNull(format, "format");
        peptides = List.copyOf(peptides);
        issues = List.copyOf(issues);
    }

    /** Returns the sample names, in order of first appearance. */
    public List<String> samples() {
        LinkedHashSet<String> samples = new LinkedHashSet<>();
        peptides.forEach(p -> samples.add(p.sample()));
        return List.copyOf(samples);
    }
}
