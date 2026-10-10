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

import { render } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { messagesOf, MessagesContext } from '../i18n';
import { ANALYSIS, DEAMIDATION, ISOMERIZATION, OXIDATION } from '../test/fixtures';
import { segments, SequenceView, styleOf } from './SequenceView';

const [HEAVY] = ANALYSIS.chains;

describe('segments', () => {
  it('splits a chain at region boundaries, blocks of ten and liability sites', () => {
    const result = segments(HEAVY, [DEAMIDATION, ISOMERIZATION, OXIDATION]);

    expect(result.map((s) => [s.text, s.style, s.sites.length, s.blockStart])).toEqual([
      ['EVQL', 'framework', 0, false],
      ['N', 'cdr', 1, false],
      ['GSG', 'cdr', 0, false],
      ['D', 'cdr', 1, false],
      ['Y', 'cdr', 0, false],
      ['WGQGTLVTVS', 'framework', 0, true],
      ['S', 'framework', 0, true],
      ['KAS', 'constant', 0, false],
    ]);
  });

  it('gathers the liabilities of a residue', () => {
    const glycosylation = { ...DEAMIDATION, type: 'N_GLYCOSYLATION' as const };

    const site = segments(HEAVY, [DEAMIDATION, glycosylation])[1];

    expect(site.sites).toEqual([DEAMIDATION, glycosylation]);
  });

  it('gives each region its style', () => {
    expect(styleOf('CDR3')).toBe('cdr');
    expect(styleOf('FR4')).toBe('framework');
    expect(styleOf('CONSTANT')).toBe('constant');
  });
});

describe('SequenceView', () => {
  it('underlines the sites with the color of their worst risk and describes them', () => {
    const { container } = render(
      <MessagesContext.Provider value={messagesOf('en')}>
        <SequenceView chains={ANALYSIS.chains} liabilities={ANALYSIS.liabilities} />
      </MessagesContext.Provider>,
    );

    expect(container.textContent).toContain('Heavy (heavy, 24 residues)');
    const sites = container.querySelectorAll('.liability-site');
    expect([...sites].map((s) => s.className)).toEqual([
      'residue residue-cdr liability-site risk-high',
      'residue residue-cdr liability-site risk-medium',
      'residue residue-framework liability-site risk-low',
    ]);
    expect(sites[0].getAttribute('title')).toBe(
      'N5: Deamidation (NG), High risk\nN5: N-glycosylation (NGS), Low risk',
    );
  });
});
