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

package com.bioinfo.mabscope.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class AppInfoTest {

    private static InputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void versionComesFromTheMavenBuild() {
        assertTrue(AppInfo.version().matches("\\d+\\.\\d+\\.\\d+.*"), AppInfo.version());
    }

    @Test
    void missingOrUnfilteredVersionsFallBackToDev() {
        assertEquals("1.2.3", AppInfo.readVersion(text("version=1.2.3")));
        assertEquals("dev", AppInfo.readVersion(null));
        assertEquals("dev", AppInfo.readVersion(text("version=${project.version}")));
        assertEquals("dev", AppInfo.readVersion(text("other=1")));
        assertEquals(
                "dev",
                AppInfo.readVersion(
                        new InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("broken");
                            }
                        }));
    }
}
