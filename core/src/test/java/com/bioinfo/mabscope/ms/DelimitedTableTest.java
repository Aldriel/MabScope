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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

class DelimitedTableTest {

    @Test
    void tabSeparated() {
        DelimitedTable t = DelimitedTable.parse("Sequence\tIntensity\nPEPTIDE\t1000\n");
        assertEquals(List.of("Sequence", "Intensity"), t.header());
        assertEquals(1, t.rows().size());
        assertEquals("PEPTIDE", t.rows().getFirst().get("sequence"));
        assertEquals("1000", t.rows().getFirst().get(" INTENSITY "));
        assertEquals(2, t.rows().getFirst().line());
    }

    @Test
    void commaSeparatedWithQuotes() {
        DelimitedTable t =
                DelimitedTable.parse(
                        "name,comment\r\n\"a,b\",\"say \"\"hi\"\"\"\n\n  c  ,\"unclosed\n");
        assertEquals(2, t.rows().size());
        assertEquals("a,b", t.rows().get(0).get("name"));
        assertEquals("say \"hi\"", t.rows().get(0).get("comment"));
        assertEquals("c", t.rows().get(1).get("name"));
        assertEquals("unclosed", t.rows().get(1).get("comment"));
        assertEquals(4, t.rows().get(1).line(), "blank lines keep the numbering");
    }

    @Test
    void semicolonSeparated() {
        DelimitedTable t = DelimitedTable.parse("\uFEFFsequence;intensity\nPEPTIDE;1,5\n");
        assertEquals(List.of("sequence", "intensity"), t.header(), "BOM removed");
        assertEquals("1,5", t.rows().getFirst().get("intensity"));
    }

    @Test
    void semicolonInsideACommaHeader() {
        DelimitedTable t = DelimitedTable.parse("a;b,c\n1,2\n");
        assertEquals(List.of("a;b", "c"), t.header());
    }

    @Test
    void missingColumnsAndShortRows() {
        DelimitedTable t = DelimitedTable.parse("a,b,a\n1\n");
        DelimitedTable.Row row = t.rows().getFirst();
        assertEquals("1", row.get("a"), "first of the duplicated columns");
        assertEquals("", row.get("b"), "row shorter than the header");
        assertEquals("", row.get("z"));
        assertTrue(t.hasColumn("B"));
        assertFalse(t.hasColumn("z"));
        assertEquals("1", row.first(List.of("z", "a")));
        assertEquals("", row.first(List.of("y", "z")));
    }

    @Test
    void headerOnlyAndEmpty() {
        assertTrue(DelimitedTable.parse("a,b\n").rows().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> DelimitedTable.parse(" \n\n"));
        assertThrows(NullPointerException.class, () -> DelimitedTable.parse(null));
        assertThrows(NullPointerException.class, () -> DelimitedTable.read(null));
    }

    @Test
    void readFailure() {
        Reader broken =
                new Reader() {
                    @Override
                    public int read(char[] buffer, int offset, int length) throws IOException {
                        throw new IOException("disk error");
                    }

                    @Override
                    public void close() {}
                };
        assertThrows(IOException.class, () -> DelimitedTable.read(broken));
    }
}
