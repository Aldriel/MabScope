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

package com.bioinfo.mabscope.region;

import com.bioinfo.mabscope.model.Region;

import java.util.Objects;

/**
 * Thrown when the conserved residues around a CDR cannot be found, typically because the sequence
 * is not an antibody variable domain or is of the other chain type.
 */
public final class RegionNotFoundException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    /** The CDR that could not be located. */
    private final Region region;

    /** Name of the chain being annotated. */
    private final String chainName;

    /**
     * Creates the exception.
     *
     * @param chainName name of the chain being annotated
     * @param region the CDR that could not be located
     * @param reason which conserved residue is missing
     */
    public RegionNotFoundException(String chainName, Region region, String reason) {
        super(
                Objects.requireNonNull(region, "region")
                        + " not found in chain '"
                        + chainName
                        + "': "
                        + reason);
        this.region = region;
        this.chainName = chainName;
    }

    /** Returns the CDR that could not be located. */
    public Region region() {
        return region;
    }

    /** Returns the name of the chain being annotated. */
    public String chainName() {
        return chainName;
    }
}
