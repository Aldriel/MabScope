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

interface Props {
  value: number;
  min: number;
  max: number;
  /** Change made by the arrows; 1 for a whole number. */
  step?: number;
  onChange: (value: number) => void;
  title?: string;
  label: string;
  id?: string;
}

/**
 * Number field with arrows, like a JavaFX Spinner. The arrows (one step up or down) change the
 * value at once; a typed value is committed, clamped to the allowed range, when the field loses
 * the focus or Enter is pressed. A comma is accepted as decimal separator.
 */
export function NumberField({ value, min, max, step = 1, onChange, title, label, id }: Props) {
  const [text, setText] = useState<string | null>(null);

  const parse = (raw: string) => {
    const parsed = Number(raw.replace(',', '.'));
    const valid = raw.trim() !== '' && Number.isFinite(parsed);
    return valid && (step !== 1 || Number.isInteger(parsed)) ? parsed : null;
  };

  const commit = (raw: string) => {
    const parsed = parse(raw);
    const clamped = Math.min(max, Math.max(min, parsed ?? value));
    if (clamped !== value) {
      onChange(clamped);
    }
    setText(null);
  };

  return (
    <input
      id={id}
      type="number"
      className="number-field"
      min={min}
      max={max}
      step={step}
      value={text ?? value}
      title={title}
      aria-label={label}
      onChange={(e) => {
        const parsed = parse(e.target.value);
        const current = parse(text ?? String(value));
        const arrow = parsed !== null && current !== null;
        if (arrow && Math.abs(Math.abs(parsed - current) - step) < 1e-9) {
          commit(e.target.value);
        } else {
          setText(e.target.value);
        }
      }}
      onBlur={(e) => commit(e.target.value)}
      onKeyDown={(e) => {
        if (e.key === 'Enter') {
          e.preventDefault();
          commit(e.currentTarget.value);
        }
      }}
    />
  );
}
