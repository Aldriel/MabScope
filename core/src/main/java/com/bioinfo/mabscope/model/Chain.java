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

import com.bioinfo.proteomics.model.AminoAcid;

import java.util.Locale;
import java.util.Objects;

/**
 * One polypeptide chain of an antibody, as written in one-letter code from the N-terminus.
 *
 * @param name display name, such as {@code Heavy chain}
 * @param type heavy or light
 * @param sequence residues in upper case; only the 20 standard amino acids are accepted
 */
public record Chain(String name, ChainType type, String sequence) {

    /** Validates the chain and puts its sequence in upper case. */
    public Chain {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sequence, "sequence");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Chain name must not be blank");
        }
        sequence = sequence.toUpperCase(Locale.ROOT);
        if (sequence.isEmpty()) {
            throw new IllegalArgumentException("Chain '" + name + "' has no residue");
        }
        for (int i = 0; i < sequence.length(); i++) {
            char c = sequence.charAt(i);
            if (!AminoAcid.isValidCode(c)) {
                throw new IllegalArgumentException(
                        "Invalid amino acid '"
                                + c
                                + "' at position "
                                + (i + 1)
                                + " of chain '"
                                + name
                                + "'");
            }
        }
    }

    /** Returns the number of residues. */
    public int length() {
        return sequence.length();
    }

    /**
     * Returns the residue at a position.
     *
     * @param position 1-based position, from the N-terminus
     * @return the one-letter code of the residue
     * @throws IndexOutOfBoundsException if the position is not within the chain
     */
    public char residueAt(int position) {
        Objects.checkIndex(position - 1, sequence.length());
        return sequence.charAt(position - 1);
    }

    /**
     * Returns the residues between two positions.
     *
     * @param start 1-based position of the first residue
     * @param end 1-based position of the last residue, inclusive
     * @return the subsequence
     * @throws IndexOutOfBoundsException if the range is not within the chain
     */
    public String subsequence(int start, int end) {
        Objects.checkFromToIndex(start - 1, end, sequence.length());
        return sequence.substring(start - 1, end);
    }
}
