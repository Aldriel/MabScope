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

import com.bioinfo.proteomics.parser.ParseResult;

/**
 * Adapter (GoF pattern) turning the result table of one MS software into {@link ObservedPeptide}s,
 * so that the quantification does not depend on the software that produced the data.
 */
public interface MsResultAdapter {

    /** Returns the file format handled by this adapter. */
    MsFormat format();

    /**
     * Tells whether a table has the columns of this format, for automatic detection.
     *
     * @param table the table read from the file
     * @return true when every required column is present
     */
    boolean recognizes(DelimitedTable table);

    /**
     * Converts the rows of a table. Rows that cannot be read are skipped and reported.
     *
     * @param table the table read from the file
     * @return the observed peptides, and the issues found on the skipped rows
     * @throws com.bioinfo.proteomics.parser.ParsingException if a required column is missing
     */
    ParseResult<ObservedPeptide> adapt(DelimitedTable table);
}
