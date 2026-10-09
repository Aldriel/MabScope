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

package com.bioinfo.mabscope.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

class ModelTest {

    @Nested
    class Chains {

        @Test
        void sequenceIsUpperCased() {
            Chain c = new Chain("Light chain", ChainType.LIGHT, "diqmtq");
            assertEquals("DIQMTQ", c.sequence());
            assertEquals(6, c.length());
            assertEquals('D', c.residueAt(1));
            assertEquals('Q', c.residueAt(6));
            assertEquals("IQM", c.subsequence(2, 4));
        }

        @Test
        void invalidResidueIsLocated() {
            IllegalArgumentException e =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> new Chain("Heavy chain", ChainType.HEAVY, "EVQXVE"));
            assertEquals(
                    "Invalid amino acid 'X' at position 4 of chain 'Heavy chain'", e.getMessage());
        }

        @Test
        void validation() {
            assertThrows(NullPointerException.class, () -> new Chain(null, ChainType.HEAVY, "A"));
            assertThrows(NullPointerException.class, () -> new Chain("H", null, "A"));
            assertThrows(NullPointerException.class, () -> new Chain("H", ChainType.HEAVY, null));
            assertThrows(
                    IllegalArgumentException.class, () -> new Chain(" ", ChainType.HEAVY, "A"));
            assertThrows(
                    IllegalArgumentException.class, () -> new Chain("H", ChainType.HEAVY, ""));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Chain("H", ChainType.HEAVY, "EV QL"));
        }

        @Test
        void positionsOutsideTheChain() {
            Chain c = new Chain("H", ChainType.HEAVY, "EVQL");
            assertThrows(IndexOutOfBoundsException.class, () -> c.residueAt(0));
            assertThrows(IndexOutOfBoundsException.class, () -> c.residueAt(5));
            assertThrows(IndexOutOfBoundsException.class, () -> c.subsequence(0, 2));
            assertThrows(IndexOutOfBoundsException.class, () -> c.subsequence(3, 5));
            assertThrows(IndexOutOfBoundsException.class, () -> c.subsequence(3, 1));
        }
    }

    @Nested
    class Antibodies {

        @Test
        void heavyAndLightChain() {
            Antibody ab = Antibody.of("Test", "EVQL", "DIQM");
            assertEquals(2, ab.chains().size());
            assertEquals(ChainType.HEAVY, ab.chain("Heavy chain").orElseThrow().type());
            assertEquals(ChainType.LIGHT, ab.chain("Light chain").orElseThrow().type());
            assertTrue(ab.chain("Other").isEmpty());
        }

        @Test
        void chainsAreCopied() {
            List<Chain> chains = new ArrayList<>(List.of(new Chain("H", ChainType.HEAVY, "EV")));
            Antibody ab = new Antibody("Test", chains);
            chains.add(new Chain("L", ChainType.LIGHT, "DI"));
            assertEquals(1, ab.chains().size());
            assertThrows(UnsupportedOperationException.class, () -> ab.chains().clear());
        }

        @Test
        void validation() {
            Chain h = new Chain("H", ChainType.HEAVY, "EV");
            assertThrows(NullPointerException.class, () -> new Antibody(null, List.of(h)));
            assertThrows(NullPointerException.class, () -> new Antibody("Test", null));
            assertThrows(IllegalArgumentException.class, () -> new Antibody(" ", List.of(h)));
            assertThrows(IllegalArgumentException.class, () -> new Antibody("Test", List.of()));
            IllegalArgumentException e =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new Antibody(
                                            "Test",
                                            List.of(h, new Chain("H", ChainType.LIGHT, "DI"))));
            assertEquals("Duplicate chain name 'H'", e.getMessage());
        }
    }

    @Nested
    class Regions {

        @ParameterizedTest
        @EnumSource(Region.class)
        void onlyCdrsBelongToTheBindingSite(Region region) {
            assertEquals(region.name().startsWith("CDR"), region.isCdr());
        }

        @Test
        void span() {
            RegionSpan s = new RegionSpan(Region.CDR1, 31, 35);
            assertEquals(5, s.length());
            assertTrue(s.contains(31));
            assertTrue(s.contains(35));
            assertFalse(s.contains(30));
            assertFalse(s.contains(36));
            assertEquals(1, new RegionSpan(Region.FR1, 1, 1).length());
        }

        @Test
        void spanValidation() {
            assertThrows(NullPointerException.class, () -> new RegionSpan(null, 1, 2));
            assertThrows(IllegalArgumentException.class, () -> new RegionSpan(Region.FR1, 0, 2));
            assertThrows(IllegalArgumentException.class, () -> new RegionSpan(Region.FR1, 3, 2));
        }
    }

    @Nested
    class Annotations {

        private final Chain chain = new Chain("H", ChainType.HEAVY, "AAACCAAA");

        private ChainAnnotation annotation(RegionSpan... spans) {
            return new ChainAnnotation(chain, NumberingScheme.KABAT, List.of(spans));
        }

        @Test
        void lookups() {
            ChainAnnotation a =
                    annotation(
                            new RegionSpan(Region.FR1, 1, 3),
                            new RegionSpan(Region.CDR1, 4, 5),
                            new RegionSpan(Region.FR2, 6, 8));
            assertEquals("CC", a.sequence(Region.CDR1).orElseThrow());
            assertTrue(a.span(Region.CDR2).isEmpty());
            assertTrue(a.sequence(Region.CDR2).isEmpty());
            assertEquals(Region.FR1, a.regionAt(3));
            assertEquals(Region.CDR1, a.regionAt(4));
            assertEquals(Region.FR2, a.regionAt(8));
            assertThrows(IndexOutOfBoundsException.class, () -> a.regionAt(0));
            assertThrows(IndexOutOfBoundsException.class, () -> a.regionAt(9));
        }

        @Test
        void spansMustBeContiguous() {
            IllegalArgumentException e =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    annotation(
                                            new RegionSpan(Region.FR1, 1, 3),
                                            new RegionSpan(Region.CDR1, 5, 8)));
            assertEquals("CDR1 starts at 5, not 4", e.getMessage());
        }

        @Test
        void spansMustCoverTheChain() {
            IllegalArgumentException e =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> annotation(new RegionSpan(Region.FR1, 1, 7)));
            assertEquals("Regions cover 7 of 8 residues", e.getMessage());
        }

        @Test
        void validation() {
            List<RegionSpan> spans = List.of(new RegionSpan(Region.FR1, 1, 8));
            assertThrows(
                    NullPointerException.class,
                    () -> new ChainAnnotation(null, NumberingScheme.KABAT, spans));
            assertThrows(NullPointerException.class, () -> new ChainAnnotation(chain, null, spans));
            assertThrows(
                    NullPointerException.class,
                    () -> new ChainAnnotation(chain, NumberingScheme.KABAT, null));
        }
    }
}
