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
import type { AppModel, ExampleId } from '../model/AppModel';
import { RISKS, type Liability, type Risk } from '../model/types';
import { useStore } from './useStore';

/** Tells whether a risk is at least the minimum chosen in the filter. */
export const atLeast = (risk: Risk, minimum: Risk) => RISKS.indexOf(risk) >= RISKS.indexOf(minimum);

/** Liabilities kept by the filters of the tab. */
export function filterLiabilities(
  liabilities: readonly Liability[],
  cdrOnly: boolean,
  minimumRisk: Risk,
): Liability[] {
  return liabilities.filter((l) => (!cdrOnly || l.inCdr) && atLeast(l.risk, minimumRisk));
}

/**
 * Controller of the "Antibody" tab: the sequences to analyze, the examples and the
 * demonstration, and the filters of the liability table.
 */
export function useAntibodyController(model: AppModel) {
  const input = useStore(model.input);
  const analysis = useStore(model.analysis);
  const [cdrOnly, setCdrOnly] = useState(false);
  const [minimumRisk, setMinimumRisk] = useState<Risk>('LOW');

  return {
    input,
    setInput: (changes: Parameters<AppModel['setInput']>[0]) => model.setInput(changes),
    analysis,
    liabilities: analysis ? filterLiabilities(analysis.liabilities, cdrOnly, minimumRisk) : [],
    cdrOnly,
    setCdrOnly,
    minimumRisk,
    setMinimumRisk,
    analyze: () => model.analyze(),
    loadExample: (id: ExampleId) => model.loadExample(id),
    loadDemo: () => model.loadDemo(),
  };
}

export type AntibodyController = ReturnType<typeof useAntibodyController>;
