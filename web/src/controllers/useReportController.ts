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

import { useState } from 'react';
import type { Locale } from '../i18n';
import type { AppModel, Paper, ReportOptions } from '../model/AppModel';
import { saveFile } from './navigator';
import { useStore } from './useStore';

/** Countries using the US Letter size, as on the server. */
const LETTER_COUNTRIES = ['US', 'CA'];

/**
 * Paper used where the browser is set: US Letter in North America, A4 elsewhere.
 *
 * @param languages preferred languages of the browser, such as ["fr-CA", "en"]
 */
export function defaultPaper(languages: readonly string[]): Paper {
  const country = languages[0]?.split('-')[1]?.toUpperCase() ?? '';
  return LETTER_COUNTRIES.includes(country) ? 'LETTER' : 'A4';
}

/** Time zone of the browser, such as "America/Toronto"; UTC when unknown. */
export const browserTimeZone = () => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

/**
 * Controller of the "QC report" tab: the content of the report, then its download.
 *
 * @param locale language of the interface, also the default language of the report
 * @param languages preferred languages of the browser, whose country sets the paper size
 */
export function useReportController(model: AppModel, locale: Locale, languages: readonly string[]) {
  const analysis = useStore(model.analysis);
  const quantification = useStore(model.quantification);
  const [options, setOptions] = useState<Omit<ReportOptions, 'timeZone'>>(() => ({
    language: locale,
    paper: defaultPaper(languages),
    analyst: '',
    notes: '',
    includePeptideMap: true,
    includeQuantification: true,
  }));

  return {
    options,
    change: (changes: Partial<ReportOptions>) =>
      setOptions((previous) => ({ ...previous, ...changes })),
    analysisName: analysis?.name ?? null,
    hasQuantification: quantification !== null,
    /** Has the server write the report, then the browser save it. */
    download: async () => {
      const file = await model.writeReport({ ...options, timeZone: browserTimeZone() });
      if (file !== null) {
        saveFile(file.content, file.fileName);
      }
    },
  };
}

export type ReportController = ReturnType<typeof useReportController>;
