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

package com.bioinfo.mabscope.quant;

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.liability.LiabilityType;
import com.bioinfo.mabscope.mapping.Glycan;
import com.bioinfo.mabscope.mapping.PeptideMapper;
import com.bioinfo.mabscope.ms.ObservedPeptide;
import com.bioinfo.mabscope.service.AntibodyAnalysis;
import com.bioinfo.mabscope.service.ChainAnalysis;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Measures the modification of each liability site from the peptides observed by LC-MS.
 *
 * <p>The observed peptides are located in the chains by their sequence. For each site and sample,
 * the percentage of a modified form is the intensity of the peptides carrying it divided by the
 * intensity of every peptide covering the site, whatever its form (relative quantification commonly
 * used in peptide mapping). The C-terminal lysine is measured by comparing the peptides ending just
 * before it with those ending on it. Isomerization, which does not change the mass, and the other
 * liabilities without a modified form are not quantified.
 */
public final class SiteQuantifier {

    /** Largest gap, in Da, between an observed mass shift and the expected one. */
    private static final double TOLERANCE = 0.02;

    /** Smallest mass shift, in Da, considered as a glycan: one HexNAc. */
    private static final double SMALLEST_GLYCAN = 203.0;

    /**
     * Quantifies the liabilities of an antibody.
     *
     * @param analysis the antibody, its regions and liabilities
     * @param observed the peptides observed in one or more samples
     * @return the coverage and the quantity of each site covered
     */
    public Quantification quantify(AntibodyAnalysis analysis, List<ObservedPeptide> observed) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(observed, "observed");
        Set<String> samples = new LinkedHashSet<>();
        Map<String, Map<String, BitSet>> covered = new LinkedHashMap<>();
        Map<Liability, Map<String, Accumulator>> sites = new LinkedHashMap<>();
        Set<String> unmatched = new LinkedHashSet<>();
        for (ObservedPeptide o : observed) {
            samples.add(o.sample());
            String sequence = o.peptide().sequence();
            boolean found = false;
            for (ChainAnalysis chain : analysis.chains()) {
                String chainSequence = chain.chain().sequence();
                for (int i = chainSequence.indexOf(sequence);
                        i >= 0;
                        i = chainSequence.indexOf(sequence, i + 1)) {
                    found = true;
                    covered.computeIfAbsent(o.sample(), s -> new LinkedHashMap<>())
                            .computeIfAbsent(chain.chain().name(), c -> new BitSet())
                            .set(i, i + sequence.length());
                    for (Liability site : chain.liabilities()) {
                        measure(site, o, i + 1, i + sequence.length(), sites);
                    }
                }
            }
            if (!found) {
                unmatched.add(sequence);
            }
        }
        return new Quantification(
                List.copyOf(samples),
                coverage(analysis, samples, covered),
                quantities(analysis, samples, sites),
                List.copyOf(unmatched));
    }

    /**
     * Returns the form expected at a site, reported at 0 % when it is not observed.
     *
     * @param site the liability
     * @return the name of the form; empty for glycosylation, whose forms are the observed glycans,
     *     and for the liabilities that are not quantified
     */
    public static Optional<String> expectedForm(Liability site) {
        return switch (site.type()) {
            case DEAMIDATION -> Optional.of(Modification.DEAMIDATED.name());
            case OXIDATION -> Optional.of(Modification.OXIDATION.name());
            case PYROGLUTAMATE ->
                    Optional.of(
                            site.motif().equals("Q")
                                    ? Modification.GLN_TO_PYRO_GLU.name()
                                    : Modification.GLU_TO_PYRO_GLU.name());
            case C_TERMINAL_LYSINE -> Optional.of(PeptideMapper.LYSINE_LOSS);
            case N_GLYCOSYLATION, ISOMERIZATION, UNPAIRED_CYSTEINE, ASP_PRO_CLEAVAGE ->
                    Optional.empty();
        };
    }

    private static void measure(
            Liability site,
            ObservedPeptide o,
            int start,
            int end,
            Map<Liability, Map<String, Accumulator>> sites) {
        if (site.type() == LiabilityType.C_TERMINAL_LYSINE) {
            if (end == site.position() || end == site.position() - 1) {
                Accumulator a = accumulator(sites, site, o.sample());
                a.total += o.intensity();
                if (end == site.position() - 1) {
                    a.add(PeptideMapper.LYSINE_LOSS, o.intensity());
                }
            }
            return;
        }
        boolean quantified =
                site.type() == LiabilityType.N_GLYCOSYLATION || expectedForm(site).isPresent();
        if (!quantified || site.position() < start || site.position() > end) {
            return;
        }
        Accumulator a = accumulator(sites, site, o.sample());
        a.total += o.intensity();
        for (Modification m : modificationsAt(o.peptide(), site.position() - start + 1)) {
            Optional<String> form = form(site.type(), m);
            if (form.isPresent()) {
                a.add(form.get(), o.intensity());
                return;
            }
        }
    }

    private static Accumulator accumulator(
            Map<Liability, Map<String, Accumulator>> sites, Liability site, String sample) {
        return sites.computeIfAbsent(site, s -> new LinkedHashMap<>())
                .computeIfAbsent(sample, s -> new Accumulator(expectedForm(site)));
    }

    /** Modifications of a residue; those of the N-terminus are attributed to the first residue. */
    private static List<Modification> modificationsAt(Peptide peptide, int position) {
        List<Modification> mods =
                new ArrayList<>(peptide.residues().get(position - 1).modifications());
        if (position == 1) {
            mods.addAll(peptide.nTermModifications());
        }
        return mods;
    }

    /** Names the form of a liability that a modification represents, if it is one. */
    private static Optional<String> form(LiabilityType type, Modification m) {
        return switch (type) {
            case DEAMIDATION -> named(m, Modification.DEAMIDATED);
            case OXIDATION ->
                    named(m, Modification.OXIDATION).or(() -> named(m, Modification.DIOXIDATION));
            case PYROGLUTAMATE ->
                    named(m, Modification.GLN_TO_PYRO_GLU)
                            .or(() -> named(m, Modification.GLU_TO_PYRO_GLU));
            case N_GLYCOSYLATION -> glycan(m);
            default -> Optional.empty();
        };
    }

    private static Optional<String> named(Modification observed, Modification expected) {
        return Math.abs(observed.monoisotopicDelta() - expected.monoisotopicDelta()) <= TOLERANCE
                ? Optional.of(expected.name())
                : Optional.empty();
    }

    private static Optional<String> glycan(Modification m) {
        for (Glycan glycan : Glycan.values()) {
            Optional<String> form = named(m, glycan.modification());
            if (form.isPresent()) {
                return form;
            }
        }
        return m.monoisotopicDelta() >= SMALLEST_GLYCAN ? Optional.of(m.name()) : Optional.empty();
    }

    private static List<ChainCoverage> coverage(
            AntibodyAnalysis analysis,
            Set<String> samples,
            Map<String, Map<String, BitSet>> covered) {
        List<ChainCoverage> coverage = new ArrayList<>();
        for (String sample : samples) {
            for (ChainAnalysis chain : analysis.chains()) {
                BitSet residues =
                        covered.getOrDefault(sample, Map.of())
                                .getOrDefault(chain.chain().name(), new BitSet());
                coverage.add(
                        new ChainCoverage(
                                chain.chain().name(),
                                sample,
                                chain.chain().length(),
                                residues.cardinality()));
            }
        }
        return coverage;
    }

    private static List<SiteQuantity> quantities(
            AntibodyAnalysis analysis,
            Set<String> samples,
            Map<Liability, Map<String, Accumulator>> sites) {
        List<SiteQuantity> quantities = new ArrayList<>();
        for (Liability site : analysis.liabilities()) {
            Map<String, Accumulator> bySample = sites.getOrDefault(site, Map.of());
            for (String sample : samples) {
                Accumulator a = bySample.get(sample);
                if (a != null) {
                    quantities.add(new SiteQuantity(site, sample, a.total, a.forms));
                }
            }
        }
        return quantities;
    }

    /** Intensities summed for a site in a sample. */
    private static final class Accumulator {
        private double total;
        private final Map<String, Double> forms = new LinkedHashMap<>();

        Accumulator(Optional<String> expected) {
            expected.ifPresent(form -> forms.put(form, 0.0));
        }

        void add(String form, double intensity) {
            forms.merge(form, intensity, Double::sum);
        }
    }
}
