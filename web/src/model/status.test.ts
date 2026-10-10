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
import { messagesOf } from '../i18n';
import { ApiError } from './api';
import { failure, fromError, info, READY } from './status';

const en = messagesOf('en');
const fr = messagesOf('fr');

describe('status', () => {
  it('writes informative and failure messages in the current language', () => {
    expect(READY.error).toBe(false);
    expect(info((m) => m.status.digesting).render(fr)).toBe(fr.status.digesting);
    const lengths = failure((m) => m.error.lengths);
    expect(lengths.error).toBe(true);
    expect(lengths.render(en)).toBe(
      'Error: the minimum peptide length exceeds the maximum length.',
    );
  });

  it('explains API errors by their kind, with the location and the detail', () => {
    const error = new ApiError('Unknown residue X', 400, 'PARSING', 'heavy chain');
    expect(fromError(error).render(en)).toBe(
      'Error: invalid data (heavy chain: Unknown residue X)',
    );
    expect(fromError(new ApiError('too big', 413, 'TOO_LARGE')).render(en)).toBe(
      'Error: file too large (too big)',
    );
    expect(
      ['INVALID_INPUT', 'NOT_AN_ANTIBODY', 'NOT_FOUND', 'UNEXPECTED'].map((kind) =>
        fromError(new ApiError('detail', 400, kind as never)).render(en),
      ),
    ).toEqual([
      'Error: invalid input (detail)',
      'Error: a sequence is not an antibody chain of its type (detail)',
      'Error: service not found (detail)',
      'Error: unexpected error (detail)',
    ]);
  });

  it('tells that the server does not answer', () => {
    expect(fromError(new ApiError('Server unreachable', 0, null)).render(en)).toBe(
      'Error: the server does not answer; try again in a moment',
    );
  });

  it('treats other failures as unexpected', () => {
    expect(fromError(new ApiError('HTTP 502', 502, null)).render(en)).toBe(
      'Error: unexpected error (HTTP 502)',
    );
    expect(fromError(new Error('boom')).render(en)).toBe('Error: unexpected error (boom)');
    expect(fromError('odd').render(en)).toBe('Error: unexpected error (odd)');
    expect(fromError(new Error('')).render(en)).toBe('Error: unexpected error');
  });
});
