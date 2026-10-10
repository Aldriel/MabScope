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

import { beforeEach, describe, expect, it } from 'vitest';
import { messagesOf } from '../i18n';
import {
  ANALYSIS,
  DEMO,
  deferred,
  EXAMPLE_LIST,
  fakeApi,
  IMPORTED,
  PEPTIDES,
  QUANTIFICATION,
  REPORT,
} from '../test/fixtures';
import { ApiError } from './api';
import {
  AppModel,
  DEFAULT_DIGESTION,
  DEMO_SOURCE,
  referenceAmong,
  reportLocale,
  type ReportOptions,
} from './AppModel';
import { info } from './status';
import type { Analysis, MsImport, Peptide } from './types';

const en = messagesOf('en');

const OPTIONS: ReportOptions = {
  language: 'fr',
  paper: 'LETTER',
  analyst: 'M. Ethier',
  notes: 'Lot release',
  includePeptideMap: true,
  includeQuantification: true,
  timeZone: 'America/Toronto',
};

let api: ReturnType<typeof fakeApi>;
let model: AppModel;

/** Status bar text, in English. */
const status = () => model.status.get().render(en);

beforeEach(() => {
  api = fakeApi();
  model = new AppModel(api);
});

/** Analyzes the antibody of the fake server. */
async function analyzed() {
  model.setInput({ name: 'Trastuzumab', heavy: 'EVQL', light: 'DIQM' });
  await model.analyze();
}

describe('AppModel', () => {
  describe('analysis', () => {
    it('analyzes the sequences and computes their peptide map', async () => {
      model.setInput({ name: ' mAb ', heavy: 'EVQL', light: 'DIQM', scheme: 'CHOTHIA' });

      const done = model.analyze();
      expect(model.busy.get()).toBe(true);
      expect(status()).toBe('Analyzing mAb…');
      await done;

      const antibody = { name: ' mAb ', heavy: 'EVQL', light: 'DIQM', scheme: 'CHOTHIA' };
      expect(api.analyze).toHaveBeenCalledWith(antibody);
      expect(api.peptideMap).toHaveBeenCalledWith(antibody, DEFAULT_DIGESTION);
      expect(api.quantify).not.toHaveBeenCalled();
      expect(model.analysis.get()?.name).toBe('mAb');
      expect(model.peptides.get()).toBe(PEPTIDES);
      expect(model.quantification.get()).toBeNull();
      expect(model.busy.get()).toBe(false);
      expect(status()).toBe('mAb analyzed: 4 liabilities, 1 high risk.');
    });

    it('names an antibody left blank', async () => {
      const done = model.analyze();
      expect(status()).toBe('Analyzing Antibody…');
      await done;
    });

    it('reports a failure in the status bar', async () => {
      api.analyze.mockRejectedValueOnce(
        new ApiError('V domain not found', 422, 'NOT_AN_ANTIBODY', 'heavy chain'),
      );

      await model.analyze();

      expect(model.analysis.get()).toBeNull();
      expect(model.status.get().error).toBe(true);
      expect(status()).toBe(
        'Error: a sequence is not an antibody chain of its type ' +
          '(heavy chain: V domain not found)',
      );
    });

    it('keeps only the answer to the latest analysis', async () => {
      const slow = deferred<Analysis>();
      api.analyze.mockReturnValueOnce(slow.promise);

      const first = model.analyze();
      model.setInput({ name: 'Second' });
      await model.analyze();
      slow.resolve({ ...ANALYSIS, name: 'First' });
      await first;

      expect(model.analysis.get()?.name).toBe('Second');
      expect(status()).toContain('Second analyzed');
    });

    it('ignores the failure of an analysis replaced by a newer one', async () => {
      const slow = deferred<Analysis>();
      api.analyze.mockReturnValueOnce(slow.promise);

      const first = model.analyze();
      await model.analyze();
      slow.reject(new Error('late'));
      await first;

      expect(model.status.get().error).toBe(false);
    });

    it('quantifies the imported results with the new antibody', async () => {
      await model.importResults(new Blob(['x']), 'evidence.txt', null);

      await analyzed();

      expect(api.quantify).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Trastuzumab' }),
        IMPORTED.peptides,
        'Lot B',
        2,
      );
      expect(model.quantification.get()).toBe(QUANTIFICATION);
    });

    it('quantifies again results imported while the analysis ran', async () => {
      await analyzed();
      const slow = deferred<Analysis>();
      api.analyze.mockReturnValueOnce(slow.promise);

      const analysis = model.analyze();
      await model.importResults(new Blob(['x']), 'evidence.txt', null);
      api.quantify.mockClear();
      slow.resolve(ANALYSIS);
      await analysis;
      await Promise.resolve();

      expect(api.quantify).toHaveBeenCalledWith(expect.anything(), IMPORTED.peptides, 'Lot B', 2);
      expect(model.imported.get()).toBe(IMPORTED);
    });
  });

  describe('examples', () => {
    it('fills in an example, analyzes it and forgets the previous results', async () => {
      await model.importResults(new Blob(['x']), 'evidence.txt', null);

      await model.loadExample('adalimumab');

      expect(model.input.get()).toMatchObject({ name: 'Adalimumab', heavy: 'QVQLQ' });
      expect(api.analyze).toHaveBeenCalledWith(expect.objectContaining({ name: 'Adalimumab' }));
      expect(model.imported.get()).toBeNull();
      expect(model.resultsSource.get()).toBeNull();
    });

    it('downloads the examples once', async () => {
      await model.loadExample('trastuzumab');
      await model.loadExample('adalimumab');

      expect(api.examples).toHaveBeenCalledTimes(1);
    });

    it('takes the first example when the server does not know the one asked', async () => {
      api.examples.mockResolvedValueOnce([EXAMPLE_LIST[1]]);

      await model.loadExample('trastuzumab');

      expect(model.input.get().name).toBe('Adalimumab');
    });

    it('downloads the examples again after a failure', async () => {
      api.examples.mockRejectedValueOnce(new ApiError('Server unreachable', 0, null));

      await model.loadExample('trastuzumab');
      expect(status()).toBe('Error: the server does not answer; try again in a moment');

      await model.loadExample('trastuzumab');
      expect(api.examples).toHaveBeenCalledTimes(2);
      expect(model.analysis.get()).not.toBeNull();
    });
  });

  describe('demonstration', () => {
    it('loads trastuzumab and three samples compared with Lot A', async () => {
      const done = model.loadDemo();
      expect(status()).toBe('Loading the demonstration…');
      await done;

      expect(model.imported.get()).toBe(DEMO);
      expect(model.resultsSource.get()).toBe(DEMO_SOURCE);
      expect(model.reference.get()).toBe('Lot A');
      expect(model.quantification.get()).toBe(QUANTIFICATION);
      expect(api.quantify).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Trastuzumab' }),
        DEMO.peptides,
        'Lot A',
        2,
      );
      expect(status()).toBe('Demonstration loaded: trastuzumab and 3 samples compared with Lot A.');
    });

    it('counts no sample when the demonstration has none', async () => {
      api.demoResults.mockResolvedValueOnce({ ...DEMO, samples: [] });

      await model.loadDemo();

      expect(model.reference.get()).toBe('');
      expect(status()).toBe('Demonstration loaded: trastuzumab and 0 samples compared with Lot A.');
    });
  });

  describe('peptide map', () => {
    const settings = { ...DEFAULT_DIGESTION, protease: 'LYS_C' as const, maxLength: 30 };

    it('refuses a minimum length above the maximum', async () => {
      await model.digest({ ...settings, minLength: 31 });

      expect(status()).toBe('Error: the minimum peptide length exceeds the maximum length.');
      expect(model.digestion.get()).toBe(DEFAULT_DIGESTION);
    });

    it('keeps the settings for the next analysis', async () => {
      await model.digest(settings);

      expect(api.peptideMap).not.toHaveBeenCalled();
      await analyzed();
      expect(api.peptideMap).toHaveBeenCalledWith(expect.anything(), settings);
    });

    it('digests the analyzed antibody again', async () => {
      await analyzed();
      const shorter: Peptide[] = [PEPTIDES[1]];
      api.peptideMap.mockResolvedValueOnce(shorter);

      const done = model.digest(settings);
      expect(status()).toBe('Digesting…');
      await done;

      expect(model.digestion.get()).toBe(settings);
      expect(model.peptides.get()).toBe(shorter);
      expect(status()).toBe('Peptide map: 1 peptides.');
    });
  });

  describe('LC-MS results', () => {
    it('imports results without an antibody, and reports the reading issues', async () => {
      const file = new Blob(['x']);

      const done = model.importResults(file, 'evidence.txt', 'MAXQUANT');
      expect(status()).toBe('Reading evidence.txt…');
      await done;

      expect(api.importResults).toHaveBeenCalledWith(file, 'MAXQUANT');
      expect(api.quantify).not.toHaveBeenCalled();
      expect(model.imported.get()).toBe(IMPORTED);
      expect(model.resultsSource.get()).toBe('evidence.txt');
      expect(model.reference.get()).toBe('Lot B');
      expect(status()).toBe('1 peptides read in 2 samples, 2 reading issues (see the tab).');
    });

    it('quantifies imported results when an antibody is analyzed', async () => {
      await analyzed();
      const clean: MsImport = { ...IMPORTED, samples: ['Lot B'], issues: [] };
      api.importResults.mockResolvedValueOnce(clean);

      await model.importResults(new Blob(['x']), 'psm.tsv', null);

      expect(model.quantification.get()).toBe(QUANTIFICATION);
      expect(status()).toBe('1 peptides read in 1 sample.');
    });

    it('keeps the reference when the new results contain it', async () => {
      await model.loadDemo();
      api.importResults.mockResolvedValueOnce({ ...IMPORTED, samples: ['Lot B', 'Lot A'] });

      await model.importResults(new Blob(['x']), 'evidence.txt', null);

      expect(model.reference.get()).toBe('Lot A');
    });

    it('compares the lots again with another reference or threshold', async () => {
      await model.loadDemo();
      api.quantify.mockClear();

      const done = model.setReference('Lot B');
      expect(status()).toBe('Comparing the lots…');
      await done;
      await model.setThreshold(5);

      expect(api.quantify).toHaveBeenLastCalledWith(expect.anything(), DEMO.peptides, 'Lot B', 5);
      expect(model.threshold.get()).toBe(5);
      expect(status()).toBe('Comparison: 1 of 2 forms to review.');
    });

    it('counts no form when the server makes no comparison', async () => {
      await model.loadDemo();
      api.quantify.mockResolvedValueOnce({ ...QUANTIFICATION, comparison: null });

      await model.setThreshold(3);

      expect(status()).toBe('Comparison: 0 of 0 forms to review.');
    });

    it('does not compare without an antibody and results', async () => {
      await model.setReference('Lot B');
      await model.importResults(new Blob(['x']), 'evidence.txt', null);
      await model.setThreshold(4);

      expect(api.quantify).not.toHaveBeenCalled();
      expect(model.threshold.get()).toBe(4);
    });

    it('forgets the results', async () => {
      await model.loadDemo();

      model.clearResults();

      expect(model.imported.get()).toBeNull();
      expect(model.quantification.get()).toBeNull();
      expect(model.reference.get()).toBe('');
      expect(model.analysis.get()).not.toBeNull();
    });

    it('ignores the answer of an import made before the results were cleared', async () => {
      const slow = deferred<MsImport>();
      api.importResults.mockReturnValueOnce(slow.promise);

      const done = model.importResults(new Blob(['x']), 'evidence.txt', null);
      model.clearResults();
      slow.resolve(IMPORTED);
      await done;

      expect(model.imported.get()).toBeNull();
    });
  });

  describe('new analysis', () => {
    it('forgets everything but the CDR definition', async () => {
      model.setInput({ scheme: 'ABM' });
      await model.loadDemo();

      model.clear();

      expect(model.input.get()).toEqual({ name: '', heavy: '', light: '', scheme: 'ABM' });
      expect(model.analysis.get()).toBeNull();
      expect(model.peptides.get()).toEqual([]);
      expect(model.imported.get()).toBeNull();
      expect(status()).toBe(en.status.ready);
    });
  });

  describe('report', () => {
    it('asks for an analysis first', async () => {
      expect(await model.writeReport(OPTIONS)).toBeNull();

      expect(api.report).not.toHaveBeenCalled();
      expect(status()).toBe('Error: analyze an antibody first.');
    });

    it('sends the analysis, the peptide map and the lot comparison', async () => {
      await model.loadDemo();

      const done = model.writeReport(OPTIONS);
      expect(status()).toBe('Writing the report…');
      const file = await done;

      expect(file).toBe(REPORT);
      expect(api.report).toHaveBeenCalledWith({
        antibody: expect.objectContaining({ name: 'Trastuzumab' }),
        locale: 'fr-CA',
        timeZone: 'America/Toronto',
        analyst: 'M. Ethier',
        notes: 'Lot release',
        digestion: DEFAULT_DIGESTION,
        peptides: DEMO.peptides,
        reference: 'Lot A',
        threshold: 2,
      });
      expect(status()).toBe('Report Trastuzumab-QC-report.pdf downloaded.');
    });

    it('leaves out the sections that are not wanted', async () => {
      await model.loadDemo();

      await model.writeReport({
        ...OPTIONS,
        includePeptideMap: false,
        includeQuantification: false,
      });

      expect(api.report).toHaveBeenCalledWith(
        expect.objectContaining({ digestion: null, peptides: [], reference: '' }),
      );
    });

    it('returns nothing when the server fails', async () => {
      await analyzed();
      api.report.mockRejectedValueOnce(new ApiError('PDF failed', 500, 'UNEXPECTED'));

      expect(await model.writeReport(OPTIONS)).toBeNull();
      expect(status()).toBe('Error: unexpected error (PDF failed)');
    });
  });

  it('shows the messages of the views', () => {
    model.report(info((m) => m.map.copied('PEPTIDE')));

    expect(status()).toBe('Sequence copied: PEPTIDE');
  });
});

describe('referenceAmong', () => {
  it('keeps the preferred sample, otherwise takes the first one', () => {
    expect(referenceAmong(['A', 'B'], 'B')).toBe('B');
    expect(referenceAmong(['A', 'B'], 'C')).toBe('A');
    expect(referenceAmong([], 'C')).toBe('');
  });
});

describe('reportLocale', () => {
  it('prints on US Letter for Canada and on A4 in Europe', () => {
    expect(reportLocale('fr', 'LETTER')).toBe('fr-CA');
    expect(reportLocale('en', 'LETTER')).toBe('en-CA');
    expect(reportLocale('fr', 'A4')).toBe('fr-FR');
    expect(reportLocale('en', 'A4')).toBe('en-GB');
  });
});
