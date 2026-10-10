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

import { afterEach, describe, expect, it, vi } from 'vitest';
import { ANALYSIS, DEAMIDATION, ISOMERIZATION, OXIDATION } from '../test/fixtures';
import { copyToClipboard, saveFile } from './navigator';
import { atLeast, filterLiabilities } from './useAntibodyController';
import { browserTimeZone, defaultPaper } from './useReportController';

afterEach(() => {
  vi.useRealTimers();
});

describe('liability filters', () => {
  it('compares the risks', () => {
    expect(atLeast('HIGH', 'MEDIUM')).toBe(true);
    expect(atLeast('MEDIUM', 'MEDIUM')).toBe(true);
    expect(atLeast('LOW', 'MEDIUM')).toBe(false);
  });

  it('keeps the sites in the CDRs and at the minimum risk', () => {
    const all = ANALYSIS.liabilities;
    expect(filterLiabilities(all, false, 'LOW')).toEqual(all);
    expect(filterLiabilities(all, true, 'LOW')).not.toContain(OXIDATION);
    expect(filterLiabilities(all, true, 'MEDIUM')).toEqual([DEAMIDATION, ISOMERIZATION]);
    expect(filterLiabilities(all, false, 'HIGH')).toEqual([DEAMIDATION]);
  });
});

describe('report settings', () => {
  it('prints on US Letter in North America, on A4 elsewhere', () => {
    expect(defaultPaper(['fr-CA', 'fr'])).toBe('LETTER');
    expect(defaultPaper(['en-us'])).toBe('LETTER');
    expect(defaultPaper(['fr-FR'])).toBe('A4');
    expect(defaultPaper(['fr'])).toBe('A4');
    expect(defaultPaper([])).toBe('A4');
  });

  it('dates the report in the time zone of the browser, or in UTC', () => {
    expect(browserTimeZone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    vi.spyOn(Intl, 'DateTimeFormat').mockReturnValue({
      resolvedOptions: () => ({ timeZone: '' }),
    } as unknown as Intl.DateTimeFormat);
    expect(browserTimeZone()).toBe('UTC');
  });
});

describe('navigator', () => {
  it('copies to the clipboard, ignoring a refusal', async () => {
    copyToClipboard('PEPTIDE');
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith('PEPTIDE');

    vi.mocked(navigator.clipboard.writeText).mockRejectedValueOnce(new Error('denied'));
    copyToClipboard('DENIED');
    await Promise.resolve();
  });

  it('saves a file through a temporary link, then releases its address', () => {
    vi.useFakeTimers();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      const link = document.querySelector('a[download]') as HTMLAnchorElement;
      expect(link.download).toBe('report.pdf');
      expect(link.href).toBe('blob:report');
    });
    const content = new Blob(['%PDF']);

    saveFile(content, 'report.pdf');

    expect(URL.createObjectURL).toHaveBeenCalledWith(content);
    expect(click).toHaveBeenCalled();
    expect(document.querySelector('a[download]')).toBeNull();
    vi.advanceTimersByTime(60_000);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:report');
  });
});
