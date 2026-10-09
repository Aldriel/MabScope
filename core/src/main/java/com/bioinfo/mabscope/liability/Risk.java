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

package com.bioinfo.mabscope.liability;

/** How likely a liability is to affect the quality of the antibody, from lowest to highest. */
public enum Risk {
    /** Worth monitoring; rarely a problem. */
    LOW,
    /** Often measurable during stability studies. */
    MEDIUM,
    /** Likely to change the product; to be quantified and, in a CDR, possibly engineered out. */
    HIGH;

    /** Returns the next level, or {@link #HIGH} when already the highest. */
    public Risk raised() {
        return this == HIGH ? HIGH : values()[ordinal() + 1];
    }
}
