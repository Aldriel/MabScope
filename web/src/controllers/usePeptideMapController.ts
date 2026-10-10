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
import type { AppModel } from '../model/AppModel';
import { info } from '../model/status';
import type { DigestionSettings, Peptide } from '../model/types';
import { copyToClipboard } from './navigator';
import { useStore } from './useStore';

/** Limits of the numeric settings, as the spinners of the desktop application. */
export const DIGESTION_LIMITS = {
  missedCleavages: { min: 0, max: 5 },
  minLength: { min: 1, max: 100 },
  maxLength: { min: 1, max: 500 },
} as const;

/**
 * Controller of the "Peptide map" tab: the digestion settings, the filter of the peptides and
 * the peptide whose modified forms are shown.
 */
export function usePeptideMapController(model: AppModel) {
  const applied = useStore(model.digestion);
  const peptides = useStore(model.peptides);
  const analysis = useStore(model.analysis);
  const [settings, setSettings] = useState<DigestionSettings>(applied);
  const [liabilitiesOnly, setLiabilitiesOnly] = useState(false);
  const [selected, setSelected] = useState<Peptide | null>(null);

  const shown = liabilitiesOnly ? peptides.filter((p) => p.liabilities.length > 0) : peptides;
  // A peptide of an older map is not in the new one, so the selection disappears by itself.
  const current = selected !== null && shown.includes(selected) ? selected : null;

  return {
    settings,
    change: (changes: Partial<DigestionSettings>) =>
      setSettings((previous) => ({ ...previous, ...changes })),
    digest: () => model.digest(settings),
    hasAnalysis: analysis !== null,
    peptides,
    shown,
    liabilitiesOnly,
    setLiabilitiesOnly,
    selected: current,
    select: setSelected,
    /** Copies a sequence in ProForma notation and confirms it in the status bar. */
    copy: (proForma: string) => {
      copyToClipboard(proForma);
      model.report(info((m) => m.map.copied(proForma)));
    },
  };
}

export type PeptideMapController = ReturnType<typeof usePeptideMapController>;
