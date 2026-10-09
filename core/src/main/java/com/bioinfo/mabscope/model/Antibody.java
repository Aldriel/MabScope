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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * An antibody, described by its distinct chains. A classical IgG is made of two identical heavy
 * chains and two identical light chains, so it is described by one heavy and one light chain.
 *
 * @param name display name, such as {@code Trastuzumab}
 * @param chains distinct chains, with distinct names (at least one)
 */
public record Antibody(String name, List<Chain> chains) {

    /** Validates the antibody and copies its chains, so that it is immutable. */
    public Antibody {
        Objects.requireNonNull(name, "name");
        chains = List.copyOf(Objects.requireNonNull(chains, "chains"));
        if (name.isBlank()) {
            throw new IllegalArgumentException("Antibody name must not be blank");
        }
        if (chains.isEmpty()) {
            throw new IllegalArgumentException("Antibody '" + name + "' has no chain");
        }
        Set<String> names = new HashSet<>();
        for (Chain chain : chains) {
            if (!names.add(chain.name())) {
                throw new IllegalArgumentException("Duplicate chain name '" + chain.name() + "'");
            }
        }
    }

    /**
     * Creates an antibody made of one heavy and one light chain.
     *
     * @param name display name
     * @param heavy heavy chain sequence
     * @param light light chain sequence
     * @return the antibody, whose chains are named {@code Heavy chain} and {@code Light chain}
     */
    public static Antibody of(String name, String heavy, String light) {
        return new Antibody(
                name,
                List.of(
                        new Chain("Heavy chain", ChainType.HEAVY, heavy),
                        new Chain("Light chain", ChainType.LIGHT, light)));
    }

    /**
     * Finds a chain by name.
     *
     * @param chainName exact name of the chain
     * @return the chain, or empty if the antibody has no chain of this name
     */
    public Optional<Chain> chain(String chainName) {
        return chains.stream().filter(c -> c.name().equals(chainName)).findFirst();
    }
}
