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

import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_HEAVY;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_LIGHT;
import static com.bioinfo.mabscope.ReferenceAntibodies.TRASTUZUMAB_VL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.liability.LiabilityScanner;
import com.bioinfo.mabscope.mapping.DigestionSettings;
import com.bioinfo.mabscope.model.Antibody;
import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.region.RegionNotFoundException;

import org.junit.jupiter.api.Test;

import java.util.List;

class AntibodyServiceTest {

    private final AntibodyService service = new AntibodyService();
    private final Antibody trastuzumab =
            Antibody.of("Trastuzumab", TRASTUZUMAB_HEAVY, TRASTUZUMAB_LIGHT);

    @Test
    void analyzeEveryChain() {
        AntibodyAnalysis a = service.analyze(trastuzumab, NumberingScheme.CHOTHIA);
        assertSame(trastuzumab, a.antibody());
        assertEquals(NumberingScheme.CHOTHIA, a.scheme());
        assertEquals(2, a.chains().size());
        ChainAnalysis heavy = a.chains().getFirst();
        assertEquals("Heavy chain", heavy.chain().name());
        assertEquals("GFNIKDT", heavy.annotation().sequence(Region.CDR1).orElseThrow());
        int total = a.chains().stream().mapToInt(c -> c.liabilities().size()).sum();
        assertEquals(total, a.liabilities().size());
        assertEquals("Heavy chain", a.liabilities().getFirst().chainName());
        assertEquals("Light chain", a.liabilities().getLast().chainName());
    }

    @Test
    void peptideMapOfEveryChain() {
        AntibodyAnalysis a = service.analyze(trastuzumab, NumberingScheme.KABAT);
        var map = service.peptideMap(a, DigestionSettings.trypsinDefaults());
        assertTrue(map.stream().anyMatch(p -> p.chainName().equals("Heavy chain")));
        assertTrue(map.stream().anyMatch(p -> p.chainName().equals("Light chain")));
    }

    @Test
    void customScanner() {
        AntibodyService none = new AntibodyService(new LiabilityScanner(List.of()));
        assertTrue(none.analyze(trastuzumab, NumberingScheme.KABAT).liabilities().isEmpty());
    }

    @Test
    void chainOfTheWrongType() {
        Antibody swapped =
                new Antibody("Swapped", List.of(new Chain("H", ChainType.HEAVY, TRASTUZUMAB_VL)));
        assertThrows(
                RegionNotFoundException.class,
                () -> service.analyze(swapped, NumberingScheme.KABAT));
    }

    @Test
    void validation() {
        assertThrows(NullPointerException.class, () -> new AntibodyService(null));
        assertThrows(
                NullPointerException.class, () -> service.analyze(null, NumberingScheme.KABAT));
        assertThrows(NullPointerException.class, () -> service.analyze(trastuzumab, null));
        assertThrows(
                NullPointerException.class,
                () -> service.peptideMap(null, DigestionSettings.trypsinDefaults()));
        AntibodyAnalysis a = service.analyze(trastuzumab, NumberingScheme.KABAT);
        assertThrows(NullPointerException.class, () -> service.peptideMap(a, null));
        assertThrows(
                NullPointerException.class,
                () -> new AntibodyAnalysis(null, NumberingScheme.KABAT, List.of()));
        assertThrows(
                NullPointerException.class,
                () -> new AntibodyAnalysis(trastuzumab, null, List.of()));
        assertThrows(NullPointerException.class, () -> new ChainAnalysis(null, List.of()));
    }
}
