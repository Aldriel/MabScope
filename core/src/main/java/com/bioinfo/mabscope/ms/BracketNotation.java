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

import com.bioinfo.proteomics.model.AminoAcid;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;
import com.bioinfo.proteomics.model.Residue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reads modified sequences where each modification follows its residue between brackets, such as
 * {@code PEPM(Oxidation (M))TIDE} in MaxQuant or {@code PEPM[+16]TIDE} in Skyline. A modification
 * written before the first residue is an N-terminal modification. Brackets may be nested.
 */
final class BracketNotation {

    private final char open;
    private final char close;
    private final Function<String, Modification> resolver;

    /**
     * Creates a reader.
     *
     * @param open opening bracket
     * @param close closing bracket
     * @param resolver turns the text between brackets into a modification; throws {@link
     *     IllegalArgumentException} when the text is unknown
     */
    BracketNotation(char open, char close, Function<String, Modification> resolver) {
        this.open = open;
        this.close = close;
        this.resolver = resolver;
    }

    /**
     * Reads a modified sequence.
     *
     * @param text the sequence
     * @return the peptide
     * @throws IllegalArgumentException if the sequence is malformed or a modification is unknown
     */
    Peptide parse(String text) {
        List<AminoAcid> aminoAcids = new ArrayList<>();
        List<List<Modification>> modifications = new ArrayList<>();
        List<Modification> nTerm = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == open) {
                int end = closingBracket(text, i);
                Modification m = resolver.apply(text.substring(i + 1, end).strip());
                if (aminoAcids.isEmpty()) {
                    nTerm.add(m);
                } else {
                    modifications.get(modifications.size() - 1).add(m);
                }
                i = end + 1;
            } else {
                aminoAcids.add(
                        AminoAcid.fromCode(c)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        "Invalid amino acid '"
                                                                + c
                                                                + "' in "
                                                                + text)));
                modifications.add(new ArrayList<>());
                i++;
            }
        }
        if (aminoAcids.isEmpty()) {
            throw new IllegalArgumentException("No residue in '" + text + "'");
        }
        List<Residue> residues = new ArrayList<>();
        for (int r = 0; r < aminoAcids.size(); r++) {
            residues.add(new Residue(aminoAcids.get(r), modifications.get(r)));
        }
        return new Peptide(residues, nTerm, List.of());
    }

    private int closingBracket(String text, int start) {
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == open) {
                depth++;
            } else if (text.charAt(i) == close && --depth == 0) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unclosed '" + open + "' in " + text);
    }
}
