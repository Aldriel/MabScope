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

import { useRef, useState } from 'react';
import type { AppModel } from '../model/AppModel';
import type { MsFormat } from '../model/types';
import { useStore } from './useStore';

/** Format chosen in the tab: recognized from the columns, or imposed. */
export type FormatChoice = 'AUTO' | MsFormat;

/** Views of the results, as the inner tabs of the desktop application. */
export const RESULT_VIEWS = ['comparison', 'sites', 'coverage'] as const;
export type ResultView = (typeof RESULT_VIEWS)[number];

/** Limits of the comparability threshold, in percentage points. */
export const THRESHOLD_LIMITS = { min: 0.1, max: 100, step: 0.5 } as const;

/**
 * Controller of the "LC-MS quantification" tab: imports the results of an MS software, chooses
 * the reference and threshold of the lot comparison, and the view of the results.
 */
export function useQuantificationController(model: AppModel) {
  const imported = useStore(model.imported);
  const source = useStore(model.resultsSource);
  const quantification = useStore(model.quantification);
  const reference = useStore(model.reference);
  const threshold = useStore(model.threshold);
  const analysis = useStore(model.analysis);
  const [format, setFormat] = useState<FormatChoice>('AUTO');
  const [view, setView] = useState<ResultView>('comparison');
  /** Hidden file field opened by the button, the File menu and Ctrl+O. */
  const fileInput = useRef<HTMLInputElement>(null);

  return {
    imported,
    source,
    quantification,
    reference,
    threshold,
    hasAnalysis: analysis !== null,
    format,
    setFormat,
    view,
    setView,
    fileInput,
    /** Asks the browser for a result file. */
    chooseFile: () => fileInput.current?.click(),
    /** Imports the file chosen by the user with the format of the tab. */
    importFile: (file: File) =>
      model.importResults(file, file.name, format === 'AUTO' ? null : format),
    loadDemo: () => model.loadDemo(),
    clear: () => model.clearResults(),
    setReference: (sample: string) => model.setReference(sample),
    setThreshold: (value: number) => model.setThreshold(value),
  };
}

export type QuantificationController = ReturnType<typeof useQuantificationController>;
