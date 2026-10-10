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

import { useMessages, type Messages } from '../i18n';
import { site } from '../i18n/texts';
import { RISKS, type ChainView, type Liability, type Region, type Risk } from '../model/types';

/** Residues per block, as in sequence databases. */
export const BLOCK = 10;

/** Kind of residues, which gives their color. */
export type ResidueStyle = 'cdr' | 'framework' | 'constant';

/** Run of residues drawn the same way: same region, and at most one liability site. */
export interface Segment {
  text: string;
  style: ResidueStyle;
  /** Liabilities of the residue, when the segment is a single liability site. */
  sites: Liability[];
  /** Whether the segment starts a new block of ten residues. */
  blockStart: boolean;
}

/** Returns the style of the residues of a region. */
export function styleOf(region: Region): ResidueStyle {
  if (region.startsWith('CDR')) {
    return 'cdr';
  }
  return region === 'CONSTANT' ? 'constant' : 'framework';
}

/**
 * Splits a chain into segments: a new one starts at each region boundary, at each block of ten
 * residues, and around each liability site.
 *
 * @param liabilities liabilities of the antibody; those of other chains are ignored
 */
export function segments(chain: ChainView, liabilities: readonly Liability[]): Segment[] {
  const sites = new Map<number, Liability[]>();
  for (const l of liabilities) {
    if (l.chain === chain.name) {
      sites.set(l.position, [...(sites.get(l.position) ?? []), l]);
    }
  }
  const regionAt = (position: number): Region =>
    chain.regions.find((r) => position >= r.start && position <= r.end)?.region ?? 'CONSTANT';

  const result: Segment[] = [];
  let run: Segment | null = null;
  for (let position = 1; position <= chain.length; position++) {
    const residue = chain.sequence.charAt(position - 1);
    const style = styleOf(regionAt(position));
    const here = sites.get(position);
    const blockStart = position > 1 && (position - 1) % BLOCK === 0;
    if (here) {
      result.push({ text: residue, style, sites: here, blockStart });
      run = null;
    } else if (run === null || blockStart || run.style !== style) {
      run = { text: residue, style, sites: [], blockStart };
      result.push(run);
    } else {
      run.text += residue;
    }
  }
  return result;
}

/** Highest risk of the liabilities of a site. */
const worstRisk = (sites: Liability[]) =>
  sites.reduce<Risk>(
    (worst, l) => (RISKS.indexOf(l.risk) > RISKS.indexOf(worst) ? l.risk : worst),
    'LOW',
  );

/** Tooltip of a site, one line per liability. */
const tooltip = (sites: Liability[], m: Messages) =>
  sites
    .map((l) => m.antibody.siteTooltip(site(l), m.type[l.type], l.motif, m.risk[l.risk]))
    .join('\n');

/**
 * Annotated view of the chains: residues in blocks of ten, CDRs and constant domains in their own
 * color, liability sites underlined with the color of their risk and described by a tooltip.
 */
export function SequenceView({
  chains,
  liabilities,
}: {
  chains: ChainView[];
  liabilities: Liability[];
}) {
  const m = useMessages();
  return (
    <div className="sequence-view">
      {chains.map((chain) => (
        <div key={chain.name} className="sequence-chain">
          <div className="bold">
            {m.antibody.chainTitle(chain.name, m.chainType[chain.type], chain.length)}
          </div>
          <div className="sequence-flow">
            {segments(chain, liabilities).map((s, index) => (
              <span key={index}>
                {s.blockStart && ' '}
                {s.sites.length === 0 ? (
                  <span className={`residue residue-${s.style}`}>{s.text}</span>
                ) : (
                  <span
                    className={`residue residue-${s.style} liability-site risk-${worstRisk(s.sites).toLowerCase()}`}
                    title={tooltip(s.sites, m)}
                  >
                    {s.text}
                  </span>
                )}
              </span>
            ))}
          </div>
        </div>
      ))}
      <p className="muted small">{m.antibody.legend}</p>
    </div>
  );
}
