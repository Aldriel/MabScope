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
import com.bioinfo.mabscope.liability.Risk;
import com.bioinfo.mabscope.model.Chain;
import com.bioinfo.mabscope.model.Region;
import com.bioinfo.mabscope.service.ChainAnalysis;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Annotated view of a chain: residues in blocks of ten, CDRs and constant domains in their own
 * color, liability sites marked with the color of their risk and described by a tooltip.
 */
final class SequenceView {

    /** Residues per block, as in sequence databases. */
    static final int BLOCK = 10;

    private SequenceView() {}

    /**
     * Builds the view of a chain.
     *
     * @param chain the analysis of the chain
     * @param m messages of the current language, for the title and tooltips
     * @return a title followed by the annotated sequence
     */
    static Node render(ChainAnalysis chain, Messages m) {
        Chain c = chain.chain();
        Label title =
                new Label(
                        m.get(
                                "antibody.chainTitle",
                                c.name(),
                                m.get("chainType." + c.type().name()),
                                c.length()));
        title.getStyleClass().add("bold");

        Map<Integer, List<Liability>> sites =
                chain.liabilities().stream().collect(Collectors.groupingBy(Liability::position));
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("sequence-flow");
        StringBuilder run = new StringBuilder();
        String runStyle = null;
        for (int position = 1; position <= c.length(); position++) {
            String style = styleOf(chain.annotation().regionAt(position));
            List<Liability> here = sites.get(position);
            boolean blockStart = position > 1 && (position - 1) % BLOCK == 0;
            if (here != null || blockStart || !style.equals(runStyle)) {
                flush(flow, run, runStyle);
                if (blockStart) {
                    flow.getChildren().add(new Text(" "));
                }
                runStyle = style;
            }
            if (here == null) {
                run.append(c.residueAt(position));
            } else {
                flow.getChildren().add(site(c.residueAt(position), style, here, m));
            }
        }
        flush(flow, run, runStyle);
        return new VBox(4, title, flow);
    }

    /** Returns the style class of the residues of a region. */
    static String styleOf(Region region) {
        if (region.isCdr()) {
            return "residue-cdr";
        }
        return region == Region.CONSTANT ? "residue-constant" : "residue-framework";
    }

    private static void flush(TextFlow flow, StringBuilder run, String style) {
        if (run.isEmpty()) {
            return;
        }
        Text text = new Text(run.toString());
        text.getStyleClass().addAll("residue", style);
        flow.getChildren().add(text);
        run.setLength(0);
    }

    /** A liability site, with the color of its highest risk and a tooltip listing its sites. */
    private static Text site(char residue, String style, List<Liability> here, Messages m) {
        Risk worst = here.stream().map(Liability::risk).max(Comparator.naturalOrder()).get();
        Text text = new Text(String.valueOf(residue));
        text.getStyleClass()
                .addAll(
                        "residue",
                        style,
                        "liability-site",
                        "risk-" + worst.name().toLowerCase(Locale.ROOT));
        String description =
                here.stream()
                        .map(
                                l ->
                                        m.get(
                                                "antibody.siteTooltip",
                                                Texts.site(l),
                                                Texts.type(m, l.type()),
                                                l.motif(),
                                                Texts.risk(m, l.risk())))
                        .collect(Collectors.joining("\n"));
        Tooltip.install(text, new Tooltip(description));
        return text;
    }
}
