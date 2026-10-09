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

/** Result files that MabScope can import. */
public enum MsFormat {
    /** The {@code evidence.txt} file of MaxQuant. */
    MAXQUANT("MaxQuant evidence.txt"),
    /** The {@code psm.tsv} file of FragPipe, quantified by IonQuant. */
    FRAGPIPE("FragPipe psm.tsv"),
    /** A Skyline custom report with one row per precursor and replicate. */
    SKYLINE("Skyline report"),
    /** A table with ProForma {@code sequence}, {@code sample} and {@code intensity} columns. */
    GENERIC("Generic CSV");

    private final String label;

    MsFormat(String label) {
        this.label = label;
    }

    /** Returns the name of the file format, for messages. */
    public String label() {
        return label;
    }
}
