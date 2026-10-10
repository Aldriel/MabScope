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

package com.bioinfo.mabscope.desktop.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bioinfo.mabscope.desktop.i18n.Messages;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.region.RegionNotFoundException;
import com.bioinfo.proteomics.parser.ParsingException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.Locale;
import java.util.stream.Stream;

class StatusMessageTest {

    private static final Messages EN = Messages.forLocale(Locale.ENGLISH);
    private static final Messages FR = Messages.forLocale(Locale.FRENCH);

    static Stream<Arguments> exceptions() {
        return Stream.of(
                Arguments.of(
                        new RegionNotFoundException("Heavy chain", Region.CDR3, "no W"),
                        "error.notAnAntibody"),
                Arguments.of(new IOException("disk full"), "error.file"),
                Arguments.of(new ParsingException("/0", "bad json"), "error.parsing"),
                Arguments.of(new IllegalArgumentException("bad input"), "error.invalidInput"),
                Arguments.of(new IllegalStateException("bad state"), "error.invalidState"),
                Arguments.of(new UnsupportedOperationException(), "error.unexpected"));
    }

    @ParameterizedTest
    @MethodSource("exceptions")
    void exceptionsMapToTranslatedKeys(Exception e, String key) {
        StatusMessage status = StatusMessage.fromException(e);
        assertEquals(key, status.key());
        assertTrue(status.error());
        assertTrue(EN.has(key) && FR.has(key), key);
    }

    @Test
    void exceptionWithoutMessageUsesItsClassName() {
        assertEquals(
                "UnsupportedOperationException",
                StatusMessage.fromException(new UnsupportedOperationException()).detail());
    }

    @Test
    void renderInfoAndErrors() {
        StatusMessage info = StatusMessage.info("status.analyzed", "Trastuzumab", 12, 3);
        assertEquals("Trastuzumab analyzed: 12 liabilities, 3 high risk.", info.render(EN));
        assertTrue(info.render(FR).startsWith("Trastuzumab analysé"), info.render(FR));
        assertNull(info.detail());
        assertFalse(info.error());

        assertEquals(
                "Error: analyze an antibody first.",
                StatusMessage.error("error.noAnalysis").render(EN));
        assertEquals(
                "Error: the file could not be read or written (disk full)",
                StatusMessage.fromException(new IOException("disk full")).render(EN));
    }

    @Test
    void choiceFormatsFollowTheNumbers() {
        assertEquals(
                "5 peptides read in 1 sample.",
                StatusMessage.info("status.imported", 5, 1).render(EN));
        assertEquals(
                "5 peptides read in 3 samples, 2 reading issues (see the tab).",
                StatusMessage.info("status.importedWithIssues", 5, 3, 2).render(EN));
    }

    @Test
    void argumentsAreDefensivelyCopied() {
        Object[] args = {"a"};
        StatusMessage status = new StatusMessage("status.analyzing", args, false, null);
        args[0] = "b";
        assertEquals("a", status.args()[0]);
        status.args()[0] = "c";
        assertEquals("a", status.args()[0]);
        assertThrows(NullPointerException.class, () -> StatusMessage.info(null));
    }
}
