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

/**
 * Access to the REST API of the Java server (server module), which exposes the calculations of
 * the shared core module. During development, Vite forwards /api to http://127.0.0.1:7071 (see
 * vite.config.ts).
 */
import type {
  Analysis,
  AntibodyRequest,
  DigestionSettings,
  ErrorKind,
  Example,
  MsFormat,
  MsImport,
  Observation,
  Peptide,
  Quantification,
  ReportFile,
  ReportRequest,
} from './types';

const BASE = '/api';

/** Name proposed for a report when the server gives none. */
const DEFAULT_REPORT_NAME = 'MabScope-QC-report.pdf';

/** Error returned by the API, or raised when the server cannot be reached (status 0). */
export class ApiError extends Error {
  /**
   * @param message technical explanation, in English, given by the server
   * @param status HTTP status code, or 0 if the server did not answer
   * @param kind category of the failure, translated for the user
   * @param location where the error lies, such as a chain or a line of a file, if known
   */
  constructor(
    message: string,
    readonly status: number,
    readonly kind: ErrorKind | null,
    readonly location?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Sends a request; every failure, including an error status, becomes an ApiError. */
async function send(path: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(BASE + path, init);
  } catch {
    throw new ApiError('Server unreachable', 0, null);
  }
  if (!response.ok) {
    let error: { error?: string; kind?: ErrorKind; location?: string } = {};
    try {
      error = (await response.json()) ?? {};
    } catch {
      // Not a JSON answer, e.g. from a proxy: the status says enough.
    }
    throw new ApiError(
      error.error ?? `HTTP ${response.status}`,
      response.status,
      error.kind ?? null,
      error.location,
    );
  }
  return response;
}

/** Sends a request and decodes the JSON answer. */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await send(path, init);
  try {
    return (await response.json()) as T;
  } catch {
    throw new ApiError(`Invalid answer (HTTP ${response.status})`, response.status, null);
  }
}

/** Options of a POST request with a JSON body. */
const jsonBody = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

/**
 * Reads the file name of a Content-Disposition header: the UTF-8 name (filename*) when given,
 * otherwise the plain one.
 *
 * @returns the name, or null if the header gives none
 */
export function attachmentName(header: string | null): string | null {
  if (header === null) {
    return null;
  }
  const encoded = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1].trim());
    } catch {
      // Malformed encoding: fall back on the plain name.
    }
  }
  const plain = /filename\s*=\s*"([^"]*)"/i.exec(header) ?? /filename\s*=\s*([^;]+)/i.exec(header);
  return plain ? plain[1].trim() : null;
}

/** Operations of the REST API. */
export const api = {
  /** Example antibodies offered by the interface. */
  examples() {
    return request<Example[]>('/examples');
  },

  /** Simulated peptide mapping of three trastuzumab samples, for the demonstration. */
  demoResults() {
    return request<MsImport>('/examples/demo-results');
  },

  /** Regions and liabilities of an antibody. */
  analyze(antibody: AntibodyRequest) {
    return request<Analysis>('/analysis', jsonBody(antibody));
  },

  /** Theoretical peptide map of an antibody. */
  peptideMap(antibody: AntibodyRequest, digestion: DigestionSettings) {
    return request<Peptide[]>('/peptide-map', jsonBody({ antibody, digestion }));
  },

  /**
   * Reads a result file of an MS software.
   *
   * @param format the format of the file, or null to recognize it from its columns
   */
  importResults(file: Blob, format: MsFormat | null) {
    return request<MsImport>(`/ms/import?format=${format ?? 'auto'}`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
      body: file,
    });
  },

  /**
   * Coverage and modification levels of the samples, compared with a reference.
   *
   * @param reference reference sample of the lot comparison; no comparison when blank
   * @param threshold comparability threshold, in percentage points
   */
  quantify(
    antibody: AntibodyRequest,
    peptides: Observation[],
    reference: string,
    threshold: number,
  ) {
    return request<Quantification>(
      '/quantify',
      jsonBody({ antibody, peptides, reference, threshold }),
    );
  },

  /** Quality control report, as a PDF file. */
  async report(body: ReportRequest): Promise<ReportFile> {
    const response = await send('/report', jsonBody(body));
    return {
      content: await response.blob(),
      fileName: attachmentName(response.headers.get('Content-Disposition')) ?? DEFAULT_REPORT_NAME,
    };
  },
};

/** Operations of the REST API, replaced by fakes in the tests. */
export type Api = typeof api;
