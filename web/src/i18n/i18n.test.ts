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

import { describe, expect, it } from 'vitest';
import { defaultLocale, isLocale, LOCALES, messagesOf } from '.';
import { mass, positions, signedMass, site } from './texts';

/** Describes the shape of a catalog: keys, kinds of values, lengths of lists and arities. */
function shape(value: unknown): unknown {
  if (typeof value === 'function') {
    return `function/${value.length}`;
  }
  if (Array.isArray(value)) {
    return value.map(shape);
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([key, v]) => [key, shape(v)]),
    );
  }
  return typeof value;
}

/** Every text function of a catalog, with its path such as "status.analyzed". */
function functionsOf(value: unknown, path = ''): [string, (...args: unknown[]) => unknown][] {
  if (typeof value === 'function') {
    return [[path, value as (...args: unknown[]) => unknown]];
  }
  if (value !== null && typeof value === 'object') {
    return Object.entries(value).flatMap(([key, v]) =>
      functionsOf(v, path ? `${path}.${key}` : key),
    );
  }
  return [];
}

describe('i18n', () => {
  it('gives the same texts in French and in English', () => {
    expect(shape(messagesOf('en'))).toEqual(shape(messagesOf('fr')));
  });

  it('writes every message with singular and plural counts', () => {
    for (const locale of LOCALES) {
      for (const [path, text] of functionsOf(messagesOf(locale))) {
        for (const count of [0, 1, 2.5]) {
          const written = text(...Array<number>(text.length).fill(count));
          expect(typeof written, `${locale}: ${path}`).toBe('string');
          expect(written, `${locale}: ${path}`).not.toBe('');
        }
      }
    }
  });

  it('agrees the nouns with their number', () => {
    const en = messagesOf('en');
    const fr = messagesOf('fr');
    expect(en.status.imported(5, 1)).toBe('5 peptides read in 1 sample.');
    expect(en.status.imported(5, 2)).toBe('5 peptides read in 2 samples.');
    expect(en.percent(2.06)).toBe('2.1%');
    expect(fr.percent(2.06)).toMatch(/^2,1\s?%$/);
  });

  it('chooses the language of the browser when it is supported', () => {
    expect(defaultLocale(['fr-CA', 'en'])).toBe('fr');
    expect(defaultLocale(['es'])).toBe('en');
    expect(defaultLocale([])).toBe('en');
    expect(isLocale('fr')).toBe(true);
    expect(isLocale('de')).toBe(false);
    expect(isLocale(null)).toBe(false);
  });
});

describe('texts', () => {
  it('writes sites, positions and masses as the desktop application', () => {
    expect(site({ motif: 'NG', position: 55 })).toBe('N55');
    expect(positions(31, 35)).toBe('31–35');
    expect(mass(1234.56789)).toBe('1234.5679');
    expect(signedMass(0.984)).toBe('+0.9840');
    expect(signedMass(-17.02655)).toBe('-17.0266');
  });
});
