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

/** Cleans the sequences pasted by the users, which often come from FASTA files or databases. */
public final class SequenceText {

    private SequenceText() {}

    /**
     * Keeps the residues of a pasted sequence: FASTA header lines ({@code >...}) are dropped, as
     * well as spaces, line breaks, position numbers and the final stop ({@code *}).
     *
     * @param text the pasted text, possibly {@code null}
     * @return the residues in upper case; empty when there is none
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder residues = new StringBuilder(text.length());
        for (String line : text.split("\\R")) {
            if (line.stripLeading().startsWith(">")) {
                continue;
            }
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (!Character.isWhitespace(c) && !Character.isDigit(c) && c != '*') {
                    residues.append(Character.toUpperCase(c));
                }
            }
        }
        return residues.toString();
    }
}
