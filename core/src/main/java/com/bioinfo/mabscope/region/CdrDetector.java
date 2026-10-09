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

import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.ChainAnnotation;
import com.bioinfo.mabscope.model.ChainType;
import com.bioinfo.mabscope.model.NumberingScheme;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.model.RegionSpan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Locates the CDRs of an antibody chain from its conserved residues, following the rules published
 * by Andrew C.R. Martin (<a href="http://www.bioinf.org.uk/abs/info.html#cdrid">"How to identify
 * the CDRs by looking at a sequence"</a>), so that no alignment or numbering software is needed.
 *
 * <p>The rules give the Kabat and AbM boundaries; the Chothia boundaries are derived from them.
 * All positions are 1-based.
 */
public final class CdrDetector {

    /** Residues that follow the variable domain: F/W-G-X-G plus the rest of FR4. */
    private static final int LIGHT_FR4_LENGTH = 10;

    private static final int HEAVY_FR4_LENGTH = 11;

    /** How far from its expected position a conserved cysteine is still accepted. */
    private static final int CYSTEINE_TOLERANCE = 2;

    // Residues that follow each CDR: the typical motif, then a looser fallback when one exists.
    private static final Pattern AFTER_L1 = Pattern.compile("W[YLF][QL]");
    private static final Pattern AFTER_L3 = Pattern.compile("FG.G");
    private static final Pattern AFTER_H1 = Pattern.compile("W[VIA]");
    private static final Pattern AFTER_H2 = Pattern.compile("[KR][LIVFTA][TSIA]");
    private static final Pattern AFTER_H3 = Pattern.compile("WG.G");
    private static final Pattern TRYPTOPHAN = Pattern.compile("W");

    /**
     * Splits a chain into its regions.
     *
     * @param chain heavy or light chain, variable domain alone or with its constant region
     * @param scheme definition of the CDR boundaries
     * @return the regions of the chain
     * @throws RegionNotFoundException if a conserved residue is missing
     */
    public ChainAnnotation annotate(Chain chain, NumberingScheme scheme) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(scheme, "scheme");
        Cdrs cdrs =
                chain.type() == ChainType.HEAVY
                        ? heavyCdrs(new Search(chain), scheme)
                        : lightCdrs(new Search(chain));
        int fr4Length = chain.type() == ChainType.HEAVY ? HEAVY_FR4_LENGTH : LIGHT_FR4_LENGTH;
        return new ChainAnnotation(chain, scheme, spans(chain.length(), cdrs, fr4Length));
    }

    // ----- Light chain -----

    private static Cdrs lightCdrs(Search s) {
        // CDR-L1 starts around residue 24, right after a cysteine, and is followed by W-Y-Q,
        // W-L-Q, W-F-Q or W-Y-L; it is 10 to 17 residues long.
        int cys1 = s.cysteineNear(Region.CDR1, 23, 3);
        int l1Start = cys1 + 1;
        int l1End = s.endBefore(Region.CDR1, l1Start, 10, 17, AFTER_L1, TRYPTOPHAN);

        // CDR-L2 always starts 16 residues after CDR-L1 and is 7 residues long.
        int l2Start = l1End + 16;
        int l2End = l2Start + 6;
        s.requireWithin(Region.CDR2, l2End);

        // CDR-L3 always starts 33 residues after CDR-L2, right after a cysteine, and is followed
        // by F-G-X-G; it is 7 to 11 residues long.
        int l3Start = s.cysteineNear(Region.CDR3, l2End + 32, CYSTEINE_TOLERANCE) + 1;
        int l3End = s.endBefore(Region.CDR3, l3Start, 7, 11, AFTER_L3, null);
        return new Cdrs(
                new RegionSpan(Region.CDR1, l1Start, l1End),
                new RegionSpan(Region.CDR2, l2Start, l2End),
                new RegionSpan(Region.CDR3, l3Start, l3End));
    }

    // ----- Heavy chain -----

    private static Cdrs heavyCdrs(Search s, NumberingScheme scheme) {
        // CDR-H1 (AbM) starts 4 residues after a cysteine near residue 22 and is followed by a
        // tryptophan, typically W-V, W-I or W-A; it is 10 to 12 residues long.
        int abmH1Start = s.cysteineNear(Region.CDR1, 22, 4) + 4;
        int h1End = s.endBefore(Region.CDR1, abmH1Start, 10, 12, AFTER_H1, TRYPTOPHAN);

        // CDR-H2 (Kabat) always starts 15 residues after CDR-H1 and is followed by
        // K/R-L/I/V/F/T/A-T/S/I/A; it is 16 to 19 residues long.
        int kabatH2Start = h1End + 15;
        int kabatH2End = s.endBefore(Region.CDR2, kabatH2Start, 16, 19, AFTER_H2, null);

        // CDR-H3 always starts 33 residues after CDR-H2, 3 residues after a cysteine (C-X-X,
        // typically C-A-R), and is followed by W-G-X-G; it is 3 to 25 residues long.
        int h3Start = s.cysteineNear(Region.CDR3, kabatH2End + 30, CYSTEINE_TOLERANCE) + 3;
        int h3End = s.endBefore(Region.CDR3, h3Start, 3, 25, AFTER_H3, null);
        RegionSpan cdr3 = new RegionSpan(Region.CDR3, h3Start, h3End);

        return switch (scheme) {
            case KABAT ->
                    new Cdrs(
                            new RegionSpan(Region.CDR1, abmH1Start + 5, h1End),
                            new RegionSpan(Region.CDR2, kabatH2Start, kabatH2End),
                            cdr3);
            // Chothia ends CDR-H1 at H32, 3 residues before H35; CDR-H2 spans H52-H56.
            case CHOTHIA ->
                    new Cdrs(
                            new RegionSpan(Region.CDR1, abmH1Start, h1End - 3),
                            new RegionSpan(Region.CDR2, kabatH2Start + 2, kabatH2End - 9),
                            cdr3);
            // AbM ends CDR-H2 at H58, 7 residues before the end of the Kabat definition (H65).
            case ABM ->
                    new Cdrs(
                            new RegionSpan(Region.CDR1, abmH1Start, h1End),
                            new RegionSpan(Region.CDR2, kabatH2Start, kabatH2End - 7),
                            cdr3);
        };
    }

    // ----- Framework and constant regions -----

    private static List<RegionSpan> spans(int length, Cdrs cdrs, int fr4Length) {
        List<RegionSpan> spans = new ArrayList<>();
        spans.add(new RegionSpan(Region.FR1, 1, cdrs.cdr1().start() - 1));
        spans.add(cdrs.cdr1());
        spans.add(new RegionSpan(Region.FR2, cdrs.cdr1().end() + 1, cdrs.cdr2().start() - 1));
        spans.add(cdrs.cdr2());
        spans.add(new RegionSpan(Region.FR3, cdrs.cdr2().end() + 1, cdrs.cdr3().start() - 1));
        spans.add(cdrs.cdr3());
        int fr4End = Math.min(length, cdrs.cdr3().end() + fr4Length);
        spans.add(new RegionSpan(Region.FR4, cdrs.cdr3().end() + 1, fr4End));
        if (fr4End < length) {
            spans.add(new RegionSpan(Region.CONSTANT, fr4End + 1, length));
        }
        return spans;
    }

    /** The three CDRs of a chain. */
    private record Cdrs(RegionSpan cdr1, RegionSpan cdr2, RegionSpan cdr3) {}

    /** Searches for conserved residues in one chain. */
    private static final class Search {
        private final Chain chain;
        private final String seq;

        Search(Chain chain) {
            this.chain = chain;
            this.seq = chain.sequence();
        }

        /**
         * Finds the cysteine closest to an expected position.
         *
         * @return its 1-based position
         */
        int cysteineNear(Region region, int expected, int tolerance) {
            // The expected positions are always greater than the tolerance.
            for (int offset = 0; offset <= tolerance; offset++) {
                for (int position : new int[] {expected - offset, expected + offset}) {
                    if (position <= seq.length() && at(position) == 'C') {
                        return position;
                    }
                }
            }
            throw notFound(
                    region,
                    "no cysteine within "
                            + tolerance
                            + " residues of position "
                            + expected);
        }

        /**
         * Finds where a CDR ends: the shortest length whose following residues match the typical
         * motif, otherwise the shortest length whose following residues match the fallback motif.
         *
         * @param typical residues expected after the CDR
         * @param fallback less specific motif, or {@code null} to accept the typical one only
         * @return 1-based position of the last residue of the CDR
         */
        int endBefore(
                Region region,
                int start,
                int minLength,
                int maxLength,
                Pattern typical,
                Pattern fallback) {
            List<Pattern> motifs = fallback == null ? List.of(typical) : List.of(typical, fallback);
            for (Pattern motif : motifs) {
                for (int length = minLength; length <= maxLength; length++) {
                    int next = start + length;
                    if (next <= seq.length() && matchesAt(next, motif)) {
                        return next - 1;
                    }
                }
            }
            throw notFound(
                    region,
                    "no "
                            + typical.pattern()
                            + " motif after "
                            + minLength
                            + " to "
                            + maxLength
                            + " residues from position "
                            + start);
        }

        void requireWithin(Region region, int position) {
            if (position > seq.length()) {
                throw notFound(region, "the chain ends at residue " + seq.length());
            }
        }

        private boolean matchesAt(int position, Pattern motif) {
            return motif.matcher(seq).region(position - 1, seq.length()).lookingAt();
        }

        private char at(int position) {
            return seq.charAt(position - 1);
        }

        private RegionNotFoundException notFound(Region region, String reason) {
            return new RegionNotFoundException(chain.name(), region, reason);
        }
    }
}
