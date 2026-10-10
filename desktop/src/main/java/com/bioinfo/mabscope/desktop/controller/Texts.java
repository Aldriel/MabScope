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

package com.bioinfo.mabscope.desktop.controller;

import com.bioinfo.mabscope.desktop.i18n.Messages;
import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.LiabilityType;
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.model.Region;

import java.util.Locale;

/** Texts displayed in the tables, shared by the controllers. */
final class Texts {

    private Texts() {}

    /**
     * Returns the residue and position of a site, such as {@code N55}.
     *
     * @param site the liability
     * @return the label of the site
     */
    static String site(Liability site) {
        return site.motif().charAt(0) + String.valueOf(site.position());
    }

    /**
     * Returns a range of positions, such as {@code 31–35}.
     *
     * @param start first position
     * @param end last position
     * @return the range
     */
    static String positions(int start, int end) {
        return start + "–" + end;
    }

    /**
     * Formats a percentage with one decimal, as written in the language of {@code m}.
     *
     * @param m messages of the current language
     * @param value percentage, from 0 to 100
     * @return such as {@code 2.1%} or {@code 2,1 %}
     */
    static String percent(Messages m, double value) {
        return m.get("format.percent", String.format(m.locale(), "%.1f", value));
    }

    /**
     * Formats a mass with four decimals, independently of the language so that it can be copied.
     *
     * @param mass mass in Da
     * @return such as {@code 1088.5193}
     */
    static String mass(double mass) {
        return String.format(Locale.ROOT, "%.4f", mass);
    }

    /** Returns the translated name of a liability type. */
    static String type(Messages m, LiabilityType type) {
        return m.get("type." + type.name());
    }

    /** Returns the translated name of a risk level. */
    static String risk(Messages m, Risk risk) {
        return m.get("risk." + risk.name());
    }

    /** Returns the translated name of a region. */
    static String region(Messages m, Region region) {
        return m.get("region." + region.name());
    }
}
