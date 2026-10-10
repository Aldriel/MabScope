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
import { api, ApiError, attachmentName } from './api';
import type { AntibodyRequest } from './types';

/** Replaces fetch by a stub answering with the given status, body and headers. */
function stubFetch(status: number, body: BodyInit, headers?: HeadersInit) {
  const fetchStub = vi.fn().mockResolvedValue(new Response(body, { status, headers }));
  vi.stubGlobal('fetch', fetchStub);
  return fetchStub;
}

/** URL and options of the only request sent. */
const sent = (fetchStub: ReturnType<typeof stubFetch>) =>
  fetchStub.mock.calls[0] as [string, RequestInit];

const ANTIBODY: AntibodyRequest = { name: 'mAb', heavy: 'EVQL', light: 'DIQM', scheme: 'KABAT' };

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api', () => {
  it('reads the examples and the demonstration results', async () => {
    const fetchStub = stubFetch(200, '[{"id":"trastuzumab"}]');
    expect(await api.examples()).toEqual([{ id: 'trastuzumab' }]);
    expect(sent(fetchStub)[0]).toBe('/api/examples');

    const demoStub = stubFetch(200, '{"samples":["Lot A"]}');
    expect(await api.demoResults()).toEqual({ samples: ['Lot A'] });
    expect(sent(demoStub)[0]).toBe('/api/examples/demo-results');
  });

  it('posts the antibody to analyze as JSON', async () => {
    const fetchStub = stubFetch(200, '{"name":"mAb"}');

    await api.analyze(ANTIBODY);

    const [url, init] = sent(fetchStub);
    expect(url).toBe('/api/analysis');
    expect(init.method).toBe('POST');
    expect(init.headers).toEqual({ 'Content-Type': 'application/json' });
    expect(JSON.parse(init.body as string)).toEqual(ANTIBODY);
  });

  it('sends the antibody and the digestion of a peptide map', async () => {
    const fetchStub = stubFetch(200, '[]');
    const digestion = {
      protease: 'LYS_C' as const,
      missedCleavages: 2,
      minLength: 4,
      maxLength: 40,
      alkylatedCysteines: false,
    };

    await api.peptideMap(ANTIBODY, digestion);

    const [url, init] = sent(fetchStub);
    expect(url).toBe('/api/peptide-map');
    expect(JSON.parse(init.body as string)).toEqual({ antibody: ANTIBODY, digestion });
  });

  it('posts result files as text, with the format or "auto"', async () => {
    const fetchStub = stubFetch(200, '{"samples":[]}');
    const file = new Blob(['Sequence\tExperiment']);

    await api.importResults(file, null);

    const [url, init] = sent(fetchStub);
    expect(url).toBe('/api/ms/import?format=auto');
    expect(init.body).toBe(file);
    expect(init.headers).toEqual({ 'Content-Type': 'text/plain; charset=utf-8' });

    const maxQuant = stubFetch(200, '{"samples":[]}');
    await api.importResults(file, 'MAXQUANT');
    expect(sent(maxQuant)[0]).toBe('/api/ms/import?format=MAXQUANT');
  });

  it('sends the observations, the reference and the threshold to quantify', async () => {
    const fetchStub = stubFetch(200, '{"samples":[]}');
    const peptides = [{ sequence: 'PEPTIDE', sample: 'Lot A', intensity: 10 }];

    await api.quantify(ANTIBODY, peptides, 'Lot A', 2.5);

    const [url, init] = sent(fetchStub);
    expect(url).toBe('/api/quantify');
    expect(JSON.parse(init.body as string)).toEqual({
      antibody: ANTIBODY,
      peptides,
      reference: 'Lot A',
      threshold: 2.5,
    });
  });

  it('returns the report with the file name chosen by the server', async () => {
    stubFetch(200, '%PDF', {
      'Content-Disposition': `attachment; filename="QC.pdf"; filename*=UTF-8''Contr%C3%B4le.pdf`,
    });

    const report = await api.report({
      antibody: ANTIBODY,
      locale: 'fr-CA',
      timeZone: 'America/Toronto',
      analyst: '',
      notes: '',
      digestion: null,
      peptides: [],
      reference: '',
      threshold: 2,
    });

    expect(report.fileName).toBe('Contrôle.pdf');
    expect(await report.content.text()).toBe('%PDF');
  });

  it('names the report itself when the server gives no name', async () => {
    stubFetch(200, '%PDF');

    const report = await api.report({} as never);

    expect(report.fileName).toBe('MabScope-QC-report.pdf');
  });

  it('turns API errors into ApiError with status, kind and location', async () => {
    stubFetch(400, '{"error":"Unknown residue X","kind":"PARSING","location":"heavy chain"}');

    const error = await api.analyze(ANTIBODY).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      name: 'ApiError',
      message: 'Unknown residue X',
      status: 400,
      kind: 'PARSING',
      location: 'heavy chain',
    });
  });

  it('reports an unreachable server with status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await api.examples().catch((e: unknown) => e);

    expect(error).toMatchObject({ status: 0, kind: null });
  });

  it('reports errors that are not JSON by their status', async () => {
    stubFetch(502, '<html>Bad gateway</html>');
    expect(await api.examples().catch((e: unknown) => e)).toMatchObject({
      message: 'HTTP 502',
      status: 502,
      kind: null,
    });

    stubFetch(500, 'null');
    expect(await api.examples().catch((e: unknown) => e)).toMatchObject({ message: 'HTTP 500' });
  });

  it('rejects successful answers that are not JSON', async () => {
    stubFetch(200, '<html></html>');

    const error = await api.examples().catch((e: unknown) => e);

    expect(error).toMatchObject({ message: 'Invalid answer (HTTP 200)', status: 200 });
  });
});

describe('attachmentName', () => {
  it('prefers the UTF-8 name, then the quoted name, then the bare name', () => {
    expect(attachmentName(`attachment; filename="a.pdf"; filename*=UTF-8''%C3%A9.pdf`)).toBe(
      'é.pdf',
    );
    expect(attachmentName('attachment; filename="rapport CQ.pdf"')).toBe('rapport CQ.pdf');
    expect(attachmentName('attachment; filename=report.pdf; size=3')).toBe('report.pdf');
  });

  it('falls back on the plain name when the UTF-8 name is malformed', () => {
    expect(attachmentName(`attachment; filename*=UTF-8''%E9%; filename="plain.pdf"`)).toBe(
      'plain.pdf',
    );
  });

  it('gives null without a name', () => {
    expect(attachmentName(null)).toBeNull();
    expect(attachmentName('inline')).toBeNull();
  });
});
