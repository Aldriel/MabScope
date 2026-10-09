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

package com.bioinfo.mabscope.mapping;

import com.bioinfo.mabscope.liability.Liability;
import com.bioinfo.mabscope.model.ChainAnnotation;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.proteomics.digest.DigestedPeptide;
import com.bioinfo.proteomics.mass.PeptideMassCalculator;
import com.bioinfo.proteomics.model.CommonModification;
import com.bioinfo.proteomics.model.Modification;
import com.bioinfo.proteomics.model.Peptide;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builds the theoretical peptide map of a chain: its peptides after digestion, the liabilities each
 * one contains and the masses of their modified forms, which are the masses to extract from the
 * LC-MS data to quantify each site.
 */
public final class PeptideMapper {

    /** Name given to the heavy chain without its C-terminal lysine. */
    public static final String LYSINE_LOSS = "Lys loss";

    private static final PeptideMassCalculator MASSES = PeptideMassCalculator.monoisotopic();

    private final DigestionSettings settings;

    /**
     * Creates a mapper.
     *
     * @param settings enzyme, peptide limits and alkylation
     */
    public PeptideMapper(DigestionSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Digests a chain.
     *
     * @param annotation the chain and its regions
     * @param liabilities liabilities of the chain, as found by the scanner
     * @return the peptides, in order of position
     */
    public List<MappedPeptide> map(ChainAnnotation annotation, List<Liability> liabilities) {
        Objects.requireNonNull(annotation, "annotation");
        Objects.requireNonNull(liabilities, "liabilities");
        String chainName = annotation.chain().name();
        List<MappedPeptide> peptides = new ArrayList<>();
        for (DigestedPeptide d : settings.digester().digest(annotation.chain().sequence())) {
            Peptide base = Peptide.of(d.sequence());
            if (settings.alkylatedCysteines()) {
                base = CommonModification.CARBAMIDOMETHYL.applyTo(base);
            }
            List<Liability> inside =
                    liabilities.stream()
                            .filter(l -> l.chainName().equals(chainName))
                            .filter(l -> l.position() >= d.start() && l.position() <= d.end())
                            .toList();
            List<ModifiedForm> forms = new ArrayList<>();
            for (Liability l : inside) {
                forms.addAll(modifiedForms(base, d.start(), l));
            }
            peptides.add(
                    new MappedPeptide(
                            chainName,
                            d.sequence(),
                            d.start(),
                            d.end(),
                            d.missedCleavages(),
                            base.toProForma(),
                            MASSES.neutralMass(base),
                            regions(annotation, d),
                            inside,
                            forms));
        }
        return peptides;
    }

    /** Returns the forms of a peptide modified at a liability; none when the mass is unchanged. */
    private static List<ModifiedForm> modifiedForms(Peptide base, int peptideStart, Liability l) {
        int site = l.position() - peptideStart + 1;
        return switch (l.type()) {
            case DEAMIDATION -> List.of(form(base, site, l, Modification.DEAMIDATED));
            case OXIDATION -> List.of(form(base, site, l, Modification.OXIDATION));
            case PYROGLUTAMATE ->
                    List.of(
                            form(
                                    base,
                                    site,
                                    l,
                                    l.motif().equals("Q")
                                            ? Modification.GLN_TO_PYRO_GLU
                                            : Modification.GLU_TO_PYRO_GLU));
            case N_GLYCOSYLATION -> {
                List<ModifiedForm> glycoforms = new ArrayList<>();
                for (Glycan glycan : Glycan.values()) {
                    glycoforms.add(form(base, site, l, glycan.modification()));
                }
                yield glycoforms;
            }
            case C_TERMINAL_LYSINE -> List.of(withoutLastResidue(base, l));
            // Isoaspartate has the mass of aspartate; the other liabilities are not mass shifts.
            case ISOMERIZATION, UNPAIRED_CYSTEINE, ASP_PRO_CLEAVAGE -> List.of();
        };
    }

    private static ModifiedForm form(Peptide base, int site, Liability l, Modification mod) {
        Peptide modified = base.withModificationAt(site, mod);
        return new ModifiedForm(
                l.type(),
                l.position(),
                mod.name(),
                modified.toProForma(),
                MASSES.neutralMass(modified));
    }

    private static ModifiedForm withoutLastResidue(Peptide base, Liability l) {
        Peptide clipped =
                new Peptide(
                        base.residues().subList(0, base.length() - 1),
                        base.nTermModifications(),
                        base.cTermModifications());
        return new ModifiedForm(
                l.type(),
                l.position(),
                LYSINE_LOSS,
                clipped.toProForma(),
                MASSES.neutralMass(clipped));
    }

    private static List<Region> regions(ChainAnnotation annotation, DigestedPeptide d) {
        Set<Region> regions = new LinkedHashSet<>();
        for (int position = d.start(); position <= d.end(); position++) {
            regions.add(annotation.regionAt(position));
        }
        return List.copyOf(regions);
    }
}
