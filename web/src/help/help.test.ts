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
import { HELP_SECTIONS } from '../controllers/navigator';
import { LOCALES } from '../i18n';
import { guideOf, imageUrl } from './guides';
import { fold, highlight, inline, parseGuide, search } from './helpDocument';

const GUIDE = `Text before the first section is ignored.

<!-- section: intro -->
# Démarrage

Une **tolérance** de 2 points, voir \`PEPTIDE\` ou [IMGT](https://www.imgt.org).

> Note sur
> deux lignes.

- premier
  suite
- second

1. un
2. deux

## Sous-titre

### Détail

![Capture](images/fr/absent.png)

| Élément | Description |
|---|---|
| Seuil | Écart maximal |

# Second chapter

\`\`\`
{ "id": 1 }
\`\`\`
`;

describe('helpDocument', () => {
  const sections = parseGuide(GUIDE);

  it('splits the guide into sections made of blocks', () => {
    expect(sections.map((s) => [s.id, s.title])).toEqual([
      ['intro', 'Démarrage'],
      ['second-chapter', 'Second chapter'],
    ]);
    expect(sections[0].blocks.map((b) => b.kind)).toEqual([
      'paragraph',
      'note',
      'list',
      'list',
      'heading',
      'heading',
      'image',
      'table',
    ]);
    expect(sections[1].blocks).toEqual([{ kind: 'code', text: '{ "id": 1 }' }]);
  });

  it('reads the inline styles', () => {
    expect(inline('a **b** `c` [d](e) *f*')).toEqual([
      { text: 'a ', style: 'PLAIN' },
      { text: 'b', style: 'BOLD' },
      { text: ' ', style: 'PLAIN' },
      { text: 'c', style: 'CODE' },
      { text: ' ', style: 'PLAIN' },
      { text: 'd', style: 'LINK', url: 'e' },
      { text: ' ', style: 'PLAIN' },
      { text: 'f', style: 'ITALIC' },
    ]);
  });

  it('searches without regard to case or accents, and counts the occurrences', () => {
    expect(fold('Écart É')).toBe('ecart e');
    const hits = search(sections, 'TOLERANCE');
    expect(hits.map((h) => [h.section.id, h.occurrences])).toEqual([['intro', 1]]);
    expect(search(sections, 'tolérance absent')).toEqual([]);
    expect(highlight('Une Tolérance', ['tolerance'])).toEqual([
      { text: 'Une ', match: false },
      { text: 'Tolérance', match: true },
    ]);
  });
});

describe('guides', () => {
  it('gives a section of each guide to every tab, in both languages', () => {
    for (const locale of LOCALES) {
      const ids = guideOf(locale).map((s) => s.id);
      expect(ids).toEqual(expect.arrayContaining(Object.values(HELP_SECTIONS)));
    }
    expect(guideOf('fr').map((s) => s.id)).toEqual(guideOf('en').map((s) => s.id));
  });

  it('parses each guide once', () => {
    expect(guideOf('en')).toBe(guideOf('en'));
  });

  it('finds no address for a missing screenshot', () => {
    expect(imageUrl('images/en/absent.png')).toBeNull();
  });
});
