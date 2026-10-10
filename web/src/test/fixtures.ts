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

/**
 * Small but consistent answers of the server, and a fake API returning them, for the unit tests
 * of the model, the controllers and the views.
 */
import { vi } from 'vitest';
import type { Api } from '../model/api';
import type {
  Analysis,
  Example,
  Liability,
  MsImport,
  Peptide,
  Quantification,
  ReportFile,
} from '../model/types';

const HEAVY = 'EVQLNGSGDYWGQGTLVTVSSKAS';
const LIGHT = 'DIQMTQSPSSLSASVGDRVTITC';

export const DEAMIDATION: Liability = {
  chain: 'Heavy',
  type: 'DEAMIDATION',
  position: 5,
  motif: 'NG',
  region: 'CDR1',
  risk: 'HIGH',
  inCdr: true,
};
export const GLYCOSYLATION: Liability = {
  ...DEAMIDATION,
  type: 'N_GLYCOSYLATION',
  motif: 'NGS',
  risk: 'LOW',
};
export const ISOMERIZATION: Liability = {
  chain: 'Heavy',
  type: 'ISOMERIZATION',
  position: 9,
  motif: 'DY',
  region: 'CDR1',
  risk: 'MEDIUM',
  inCdr: true,
};
export const OXIDATION: Liability = {
  chain: 'Light',
  type: 'OXIDATION',
  position: 4,
  motif: 'M',
  region: 'FR1',
  risk: 'LOW',
  inCdr: false,
};

export const ANALYSIS: Analysis = {
  name: 'Trastuzumab',
  scheme: 'KABAT',
  chains: [
    {
      name: 'Heavy',
      type: 'HEAVY',
      sequence: HEAVY,
      length: HEAVY.length,
      regions: [
        { region: 'FR1', start: 1, end: 4, cdr: false, sequence: 'EVQL' },
        { region: 'CDR1', start: 5, end: 10, cdr: true, sequence: 'NGSGDY' },
        { region: 'FR2', start: 11, end: 21, cdr: false, sequence: 'WGQGTLVTVSS' },
      ],
    },
    {
      name: 'Light',
      type: 'LIGHT',
      sequence: LIGHT,
      length: LIGHT.length,
      regions: [{ region: 'FR1', start: 1, end: 23, cdr: false, sequence: LIGHT }],
    },
  ],
  liabilities: [DEAMIDATION, GLYCOSYLATION, ISOMERIZATION, OXIDATION],
};

export const PEPTIDES: Peptide[] = [
  {
    chain: 'Heavy',
    start: 1,
    end: 22,
    sequence: 'EVQLNGSGDYWGQGTLVTVSSK',
    missedCleavages: 0,
    proForma: 'EVQLNGSGDYWGQGTLVTVSSK',
    monoisotopicMass: 2300.1234,
    regions: ['FR1', 'CDR1', 'FR2', 'CONSTANT'],
    coversCdr: true,
    liabilities: [DEAMIDATION],
    forms: [
      {
        type: 'DEAMIDATION',
        position: 5,
        modification: 'Deamidated',
        proForma: 'EVQLN[Deamidated]GSGDYWGQGTLVTVSSK',
        monoisotopicMass: 2301.1074,
      },
      {
        type: 'ISOMERIZATION',
        position: 9,
        modification: 'Succinimide',
        proForma: 'EVQLNGSGD[Dehydrated]YWGQGTLVTVSSK',
        monoisotopicMass: 2282.1128,
      },
    ],
  },
  {
    chain: 'Heavy',
    start: 23,
    end: 24,
    sequence: 'AS',
    missedCleavages: 0,
    proForma: 'AS',
    monoisotopicMass: 176.0797,
    regions: ['CONSTANT'],
    coversCdr: false,
    liabilities: [],
    forms: [],
  },
];

export const SAMPLES = ['Lot A', 'Lot B', 'Stressed'];

export const DEMO: MsImport = {
  format: 'GENERIC',
  samples: SAMPLES,
  peptides: [
    { sequence: 'EVQLNGSGDYWGQGTLVTVSSK', sample: 'Lot A', intensity: 1000 },
    { sequence: 'EVQLN[Deamidated]GSGDYWGQGTLVTVSSK', sample: 'Stressed', intensity: 100 },
  ],
  issues: [],
};

export const IMPORTED: MsImport = {
  format: 'MAXQUANT',
  samples: ['Lot B', 'Lot C'],
  peptides: [{ sequence: 'EVQLNGSGDYWGQGTLVTVSSK', sample: 'Lot B', intensity: 500 }],
  issues: [
    { severity: 'WARNING', location: 'line 3', message: 'unknown modification Foo' },
    { severity: 'ERROR', location: 'line 4', message: 'missing intensity' },
  ],
};

export const QUANTIFICATION: Quantification = {
  samples: SAMPLES,
  coverage: [
    { chain: 'Heavy', sample: 'Lot A', length: 24, coveredResidues: 22, percent: 91.7 },
    { chain: 'Light', sample: 'Lot A', length: 23, coveredResidues: 0, percent: 0 },
  ],
  sites: [
    {
      site: DEAMIDATION,
      sample: 'Stressed',
      totalIntensity: 1100,
      percents: { Deamidated: 9.1 },
      modifiedPercent: 9.1,
    },
  ],
  unmatchedSequences: ['PEPTIDE', 'SAMPLER'],
  comparison: {
    reference: 'Lot A',
    threshold: 2,
    rows: [
      {
        site: DEAMIDATION,
        form: 'Deamidated',
        percents: { 'Lot A': 0, 'Lot B': 0.5, Stressed: 9.1 },
        referencePercent: 0,
        maxDeviation: 9.1,
        flagged: true,
      },
      {
        site: ISOMERIZATION,
        form: 'Succinimide',
        percents: { 'Lot A': 1, Stressed: 1.2 },
        referencePercent: 1,
        maxDeviation: 0.2,
        flagged: false,
      },
    ],
  },
};

export const EXAMPLE_LIST: Example[] = [
  { id: 'trastuzumab', name: 'Trastuzumab', heavy: HEAVY, light: LIGHT },
  { id: 'adalimumab', name: 'Adalimumab', heavy: 'QVQLQ', light: 'DIQMT' },
];

export const REPORT: ReportFile = {
  content: new Blob(['%PDF-1.7']),
  fileName: 'Trastuzumab-QC-report.pdf',
};

/** Fake server: each operation is a mock answering at once with the data above. */
export function fakeApi() {
  return {
    examples: vi.fn(() => Promise.resolve(EXAMPLE_LIST)),
    demoResults: vi.fn(() => Promise.resolve(DEMO)),
    analyze: vi.fn((antibody: { name: string }) =>
      Promise.resolve({ ...ANALYSIS, name: antibody.name.trim() || 'Antibody' }),
    ),
    peptideMap: vi.fn(() => Promise.resolve(PEPTIDES)),
    importResults: vi.fn(() => Promise.resolve(IMPORTED)),
    quantify: vi.fn(() => Promise.resolve(QUANTIFICATION)),
    report: vi.fn(() => Promise.resolve(REPORT)),
  } satisfies Api;
}

/** Promise settled by the test, to control the order in which answers arrive. */
export function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}
