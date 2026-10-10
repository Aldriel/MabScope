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

package com.bioinfo.mabscope.examples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.liability.LiabilityType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.SequenceText;
import com.bioinfo.mabscope.ms.MsFormat;
import com.bioinfo.mabscope.ms.MsImport;
import com.bioinfo.mabscope.quant.Quantification;
import com.bioinfo.mabscope.quant.SiteComparison;
import com.bioinfo.mabscope.quant.SiteQuantity;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.AntibodyService;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

class ExamplesTest {

    private final AntibodyService service = new AntibodyService();

    @Test
    void exampleAntibodiesAreAnnotated() {
        AntibodyAnalysis adalimumab =
                service.analyze(ExampleAntibodies.adalimumab(), NumberingScheme.KABAT);
        assertEquals("Adalimumab", adalimumab.antibody().name());
        assertEquals(2, adalimumab.chains().size());
        assertEquals(450, ExampleAntibodies.trastuzumab().chains().getFirst().length());
    }

    @Test
    void demoDatasetMatchesTrastuzumab() throws IOException {
        AntibodyAnalysis analysis =
                service.analyze(ExampleAntibodies.trastuzumab(), NumberingScheme.KABAT);
        MsImport demo = service.importResults(new StringReader(ExampleAntibodies.demoResults()));
        assertEquals(MsFormat.GENERIC, demo.format());
        assertTrue(demo.issues().isEmpty(), demo.issues().toString());
        assertEquals(List.of("Lot A", "Lot B", "Stressed 40C"), demo.samples());

        Quantification q = service.quantify(analysis, demo.peptides());
        assertTrue(q.unmatchedSequences().isEmpty(), "every peptide is in trastuzumab");
        assertEquals(2.1, percent(q, 55, "Lot A", "Deamidated"), 1e-6);
        assertEquals(14.8, percent(q, 55, "Stressed 40C", "Deamidated"), 1e-6);
        assertEquals(50, percent(q, 300, "Lot B", "G0F"), 1e-3);

        List<SiteComparison> rows = service.compareLots(q, "Lot A", 2);
        assertTrue(
                rows.stream()
                        .anyMatch(
                                r ->
                                        r.site().position() == 55
                                                && r.site().type() == LiabilityType.DEAMIDATION
                                                && r.flagged()));
        assertTrue(rows.stream().anyMatch(r -> r.form().equals("G0F") && r.flagged()));
    }

    private static double percent(Quantification q, int position, String sample, String form) {
        return q.sites().stream()
                .filter(s -> s.site().chainName().equals("Heavy chain"))
                .filter(s -> s.site().position() == position && s.sample().equals(sample))
                .filter(s -> s.forms().containsKey(form))
                .mapToDouble((SiteQuantity s) -> s.percent(form))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void pastedSequencesAreCleaned() {
        assertEquals("EVQLVESGG", SequenceText.clean(">sp|heavy chain\n  1 evqlv esgg*\r\n"));
        assertEquals("", SequenceText.clean(null));
        assertEquals("", SequenceText.clean(">header only"));
    }
}
