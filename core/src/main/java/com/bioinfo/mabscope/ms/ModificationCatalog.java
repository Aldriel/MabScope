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

import com.bioinfo.mabscope.mapping.Glycan;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.parser.ModificationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The modifications that MabScope recognizes in result files, whatever the way each software names
 * them: Unimod names ({@code Deamidated}), MaxQuant names ({@code Deamidation (NQ)}, {@code ox}),
 * glycan compositions ({@code HexNAc(4)Hex(3)Fuc(1)}) or bare mass shifts ({@code +15.9949}).
 *
 * <p>Recognizing them matters: a modification resolved to its canonical form is attributed to the
 * right liability by the quantification.
 */
public final class ModificationCatalog {

    private static final Map<String, Modification> ALIASES =
            Map.of(
                    "deamidation", Modification.DEAMIDATED,
                    "ox", Modification.OXIDATION,
                    "de", Modification.DEAMIDATED,
                    "ac", Modification.ACETYL,
                    "ph", Modification.PHOSPHO,
                    "gl", Modification.GLN_TO_PYRO_GLU,
                    "cam", Modification.CARBAMIDOMETHYL);

    private final List<Modification> known = new ArrayList<>();
    private final ModificationRegistry registry = new ModificationRegistry();

    /** Creates the catalog of the common modifications and of the {@link Glycan}s. */
    public ModificationCatalog() {
        known.addAll(
                List.of(
                        Modification.CARBAMIDOMETHYL,
                        Modification.OXIDATION,
                        Modification.DIOXIDATION,
                        Modification.DEAMIDATED,
                        Modification.GLN_TO_PYRO_GLU,
                        Modification.GLU_TO_PYRO_GLU,
                        Modification.ACETYL,
                        Modification.PHOSPHO,
                        Modification.METHYL,
                        Modification.AMIDATED,
                        Modification.HEX));
        for (Glycan glycan : Glycan.values()) {
            known.add(glycan.modification());
        }
        known.forEach(registry::register);
    }

    /**
     * Returns a registry of the known modifications, for the ProForma parser.
     *
     * @return the registry, shared by the callers
     */
    public ModificationRegistry registry() {
        return registry;
    }

    /**
     * Finds a modification by name.
     *
     * @param name the name, possibly followed by its sites, such as {@code Oxidation (M)}
     * @return the modification, or empty when the name is unknown
     */
    public Optional<Modification> byName(String name) {
        String bare = name.strip();
        int sites = bare.lastIndexOf(" (");
        if (sites > 0 && bare.endsWith(")")) {
            bare = bare.substring(0, sites).strip();
        }
        String key = bare.toLowerCase(Locale.ROOT);
        if (ALIASES.containsKey(key)) {
            return Optional.of(ALIASES.get(key));
        }
        for (Glycan glycan : Glycan.values()) {
            if (glycan.composition().equalsIgnoreCase(bare)) {
                return Optional.of(glycan.modification());
            }
        }
        return registry.find(bare);
    }

    /**
     * Finds the known modification closest to a mass shift.
     *
     * @param delta monoisotopic mass shift, in Da
     * @param tolerance largest accepted difference, in Da
     * @return the closest known modification within the tolerance, or else an unnamed one
     */
    public Modification byMass(double delta, double tolerance) {
        if (!Double.isFinite(delta) || !(tolerance >= 0)) {
            throw new IllegalArgumentException("Invalid mass shift or tolerance");
        }
        Modification closest = null;
        for (Modification m : known) {
            double gap = Math.abs(m.monoisotopicDelta() - delta);
            if (gap <= tolerance
                    && (closest == null || gap < Math.abs(closest.monoisotopicDelta() - delta))) {
                closest = m;
            }
        }
        return closest != null ? closest : Modification.ofMass(delta);
    }
}
