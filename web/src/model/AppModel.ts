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

import type { Api } from './api';
import { failure, fromError, info, READY, type StatusMessage } from './status';
import { Store } from './store';
import type {
  Analysis,
  AntibodyRequest,
  DigestionSettings,
  Example,
  MsFormat,
  MsImport,
  Peptide,
  Quantification,
  ReportFile,
  ReportRequest,
} from './types';

/** Sequences and CDR definition entered in the "Antibody" tab. */
export type AntibodyInput = AntibodyRequest;

/** Typical tryptic peptide map, as DigestionSettings.trypsinDefaults() on the server. */
export const DEFAULT_DIGESTION: DigestionSettings = {
  protease: 'TRYPSIN',
  missedCleavages: 1,
  minLength: 1,
  maxLength: 60,
  alkylatedCysteines: true,
};

/** Comparability threshold of the lots, in percentage points, as LotComparator on the server. */
export const DEFAULT_THRESHOLD = 2;

/** Example antibodies offered by the menus; their sequences come from the server. */
export const EXAMPLES = [
  { id: 'trastuzumab', name: 'Trastuzumab' },
  { id: 'adalimumab', name: 'Adalimumab' },
] as const;
export type ExampleId = (typeof EXAMPLES)[number]['id'];

/** Antibody and reference sample of the demonstration. */
export const DEMO_EXAMPLE: ExampleId = 'trastuzumab';
export const DEMO_REFERENCE = 'Lot A';

/** Value of {@link AppModel.resultsSource} for the results of the demonstration. */
export const DEMO_SOURCE = 'demo';

/** Name given by the server to an antibody whose name is left blank. */
const DEFAULT_NAME = 'Antibody';

/** Countries where the reports are printed on US Letter paper, as on the server. */
export type Paper = 'LETTER' | 'A4';

/** Content of the quality control report, chosen in the "QC report" tab. */
export interface ReportOptions {
  language: 'en' | 'fr';
  paper: Paper;
  analyst: string;
  notes: string;
  includePeptideMap: boolean;
  includeQuantification: boolean;
  /** Time zone of the reader, such as "America/Toronto", for the date of the report. */
  timeZone: string;
}

/** Everything computed from the sequences: the counterpart of DesktopModel.Results. */
interface Results {
  antibody: AntibodyRequest;
  analysis: Analysis;
  peptides: Peptide[];
  imported: MsImport | null;
  reference: string;
  quantification: Quantification | null;
}

/**
 * Observable state of the web application, the MVC "Model" seen by the React views, and the
 * counterpart of DesktopModel: same operations, same status messages. The calculations run on
 * the server, which keeps nothing between two requests: the model remembers the antibody
 * analyzed and the imported results, and sends them again with each request. Each operation
 * keeps only the answer to its latest call, so a slow request can never overwrite a newer one.
 */
export class AppModel {
  readonly status = new Store<StatusMessage>(READY);
  /** Whether a request is running. */
  readonly busy = new Store(false);

  /** Sequences entered, possibly as FASTA, and the CDR definition of the next analysis. */
  readonly input = new Store<AntibodyInput>({ name: '', heavy: '', light: '', scheme: 'KABAT' });
  /** Last analysis, or null. */
  readonly analysis = new Store<Analysis | null>(null);
  readonly digestion = new Store<DigestionSettings>(DEFAULT_DIGESTION);
  /** Theoretical peptide map of the analysis. */
  readonly peptides = new Store<Peptide[]>([]);

  /** Imported LC-MS results, or null. */
  readonly imported = new Store<MsImport | null>(null);
  /** Name of the imported file, {@link DEMO_SOURCE} for the demonstration, or null. */
  readonly resultsSource = new Store<string | null>(null);
  /** Coverage, modification levels and lot comparison, or null. */
  readonly quantification = new Store<Quantification | null>(null);
  /** Reference sample of the lot comparison; empty without results. */
  readonly reference = new Store('');
  /** Comparability threshold, in percentage points. */
  readonly threshold = new Store(DEFAULT_THRESHOLD);

  /** Antibody of {@link analysis}, sent again with the requests that depend on it. */
  private analyzed: AntibodyRequest | null = null;
  /** Example antibodies, downloaded once. */
  private exampleList: Promise<Example[]> | null = null;

  /** Number of requests running. */
  private runningTasks = 0;
  /** Number of the latest call of each operation. */
  private readonly calls = new Map<string, number>();

  /** @param api operations of the server */
  constructor(private readonly api: Api) {}

  // ----- Antibody -----

  /** Changes some of the fields of the "Antibody" tab. */
  setInput(changes: Partial<AntibodyInput>): void {
    this.input.set({ ...this.input.get(), ...changes });
  }

  /** Analyzes the sequences entered, then computes their peptide map and quantification. */
  analyze(): Promise<void> {
    const antibody = this.input.get();
    const name = antibody.name.trim() || DEFAULT_NAME;
    return this.run('analysis', () => this.compute(antibody, this.imported.get()), {
      progress: info((m) => m.status.analyzing(name)),
      onSuccess: (results) => this.show(results),
    });
  }

  /**
   * Fills in the sequences of an example antibody and analyzes it. The LC-MS results of the
   * previous antibody are forgotten.
   */
  loadExample(id: ExampleId): Promise<void> {
    this.clearResults();
    return this.run('analysis', async () => this.compute(await this.fillExample(id), null), {
      onSuccess: (results) => this.show(results),
    });
  }

  /**
   * Loads the demonstration: trastuzumab and simulated LC-MS results of three samples, compared
   * with {@link DEMO_REFERENCE}.
   */
  loadDemo(): Promise<void> {
    return this.run(
      'analysis',
      async () => {
        const [antibody, demo] = await Promise.all([
          this.fillExample(DEMO_EXAMPLE),
          this.api.demoResults(),
        ]);
        return this.compute(antibody, demo, DEMO_REFERENCE);
      },
      {
        progress: info((m) => m.status.loadingDemo),
        onSuccess: (results) => {
          this.nextCall('results');
          this.imported.set(results.imported);
          this.resultsSource.set(DEMO_SOURCE);
          this.show(results);
          const samples = results.imported?.samples.length ?? 0;
          this.status.set(info((m) => m.status.demoLoaded(samples)));
        },
      },
    );
  }

  /** Puts the sequences of an example in the fields and returns them. */
  private async fillExample(id: ExampleId): Promise<AntibodyRequest> {
    this.exampleList ??= this.api.examples();
    let examples: Example[];
    try {
      examples = await this.exampleList;
    } catch (e) {
      this.exampleList = null;
      throw e;
    }
    const example = examples.find((e) => e.id === id) ?? examples[0];
    this.setInput({ name: example.name, heavy: example.heavy, light: example.light });
    return this.input.get();
  }

  /**
   * Analyzes an antibody, computes its peptide map and, with results, their quantification; the
   * three requests run together.
   *
   * @param preferredReference reference kept when it is one of the samples
   */
  private async compute(
    antibody: AntibodyRequest,
    imported: MsImport | null,
    preferredReference = this.reference.get(),
  ): Promise<Results> {
    const reference = imported ? referenceAmong(imported.samples, preferredReference) : '';
    const [analysis, peptides, quantification] = await Promise.all([
      this.api.analyze(antibody),
      this.api.peptideMap(antibody, this.digestion.get()),
      imported
        ? this.api.quantify(antibody, imported.peptides, reference, this.threshold.get())
        : Promise.resolve(null),
    ]);
    return { antibody, analysis, peptides, imported, reference, quantification };
  }

  /**
   * Publishes the results of an analysis. Results imported while it ran are quantified again
   * rather than replaced by those it started with.
   */
  private show(results: Results) {
    this.analyzed = results.antibody;
    this.analysis.set(results.analysis);
    this.peptides.set(results.peptides);
    const { name, liabilities } = results.analysis;
    const high = liabilities.filter((l) => l.risk === 'HIGH').length;
    this.status.set(info((m) => m.status.analyzed(name, liabilities.length, high)));
    if (results.imported === this.imported.get()) {
      this.nextCall('quantify');
      this.reference.set(results.reference);
      this.quantification.set(results.quantification);
    } else {
      void this.compare();
    }
  }

  // ----- Peptide map -----

  /**
   * Computes the peptide map again with new digestion settings; without an analysis, the
   * settings are kept for the next one.
   */
  digest(settings: DigestionSettings): Promise<void> {
    if (settings.minLength > settings.maxLength) {
      this.status.set(failure((m) => m.error.lengths));
      return Promise.resolve();
    }
    const antibody = this.analyzed;
    if (antibody === null) {
      this.digestion.set(settings);
      return Promise.resolve();
    }
    return this.run('digestion', () => this.api.peptideMap(antibody, settings), {
      progress: info((m) => m.status.digesting),
      onSuccess: (peptides) => {
        this.digestion.set(settings);
        this.peptides.set(peptides);
        this.status.set(info((m) => m.status.digested(peptides.length)));
      },
    });
  }

  // ----- LC-MS results -----

  /**
   * Imports the results of an MS software, then quantifies them if an antibody is analyzed.
   *
   * @param format the format of the file, or null to recognize it from its columns
   */
  importResults(file: Blob, fileName: string, format: MsFormat | null): Promise<void> {
    const antibody = this.analyzed;
    return this.run(
      'results',
      async () => {
        const imported = await this.api.importResults(file, format);
        const reference = referenceAmong(imported.samples, this.reference.get());
        const quantification = antibody
          ? await this.api.quantify(antibody, imported.peptides, reference, this.threshold.get())
          : null;
        return { imported, reference, quantification };
      },
      {
        progress: info((m) => m.status.importing(fileName)),
        onSuccess: ({ imported, reference, quantification }) => {
          this.nextCall('quantify');
          this.imported.set(imported);
          this.resultsSource.set(fileName);
          this.reference.set(reference);
          this.quantification.set(quantification);
          const peptides = imported.peptides.length;
          const samples = imported.samples.length;
          const issues = imported.issues.length;
          this.status.set(
            info((m) =>
              issues === 0
                ? m.status.imported(peptides, samples)
                : m.status.importedWithIssues(peptides, samples, issues),
            ),
          );
        },
      },
    );
  }

  /** Changes the reference sample and compares the lots again. */
  setReference(reference: string): Promise<void> {
    this.reference.set(reference);
    return this.compare();
  }

  /** Changes the comparability threshold and compares the lots again. */
  setThreshold(threshold: number): Promise<void> {
    this.threshold.set(threshold);
    return this.compare();
  }

  /** Quantifies the results again with the current reference and threshold. */
  private compare(): Promise<void> {
    const antibody = this.analyzed;
    const imported = this.imported.get();
    if (antibody === null || imported === null) {
      return Promise.resolve();
    }
    const reference = this.reference.get();
    const threshold = this.threshold.get();
    return this.run(
      'quantify',
      () => this.api.quantify(antibody, imported.peptides, reference, threshold),
      {
        progress: info((m) => m.status.comparing),
        onSuccess: (quantification) => {
          this.quantification.set(quantification);
          const rows = quantification.comparison?.rows ?? [];
          const flagged = rows.filter((r) => r.flagged).length;
          this.status.set(info((m) => m.status.compared(flagged, rows.length)));
        },
      },
    );
  }

  /** Forgets the imported LC-MS results, their quantification and the lot comparison. */
  clearResults(): void {
    this.nextCall('results');
    this.nextCall('quantify');
    this.imported.set(null);
    this.resultsSource.set(null);
    this.quantification.set(null);
    this.reference.set('');
  }

  /** Forgets the sequences and every result; the CDR definition and settings are kept. */
  clear(): void {
    this.nextCall('analysis');
    this.nextCall('digestion');
    this.setInput({ name: '', heavy: '', light: '' });
    this.analyzed = null;
    this.analysis.set(null);
    this.peptides.set([]);
    this.clearResults();
    this.status.set(READY);
  }

  // ----- Report -----

  /**
   * Has the server write the quality control report of the current analysis.
   *
   * @returns the PDF file, or null if there is no analysis or the request failed
   */
  async writeReport(options: ReportOptions): Promise<ReportFile | null> {
    const antibody = this.analyzed;
    if (antibody === null) {
      this.status.set(failure((m) => m.error.noAnalysis));
      return null;
    }
    const imported = this.imported.get();
    const withResults = options.includeQuantification && imported !== null;
    const body: ReportRequest = {
      antibody,
      locale: reportLocale(options.language, options.paper),
      timeZone: options.timeZone,
      analyst: options.analyst,
      notes: options.notes,
      digestion: options.includePeptideMap ? this.digestion.get() : null,
      peptides: withResults ? imported.peptides : [],
      reference: withResults ? this.reference.get() : '',
      threshold: this.threshold.get(),
    };
    let file: ReportFile | null = null;
    await this.run('report', () => this.api.report(body), {
      progress: info((m) => m.status.writingReport),
      onSuccess: (report) => {
        file = report;
        this.status.set(info((m) => m.status.reportSaved(report.fileName)));
      },
    });
    return file;
  }

  // ----- Shared -----

  /** Shows a message in the status bar, such as the confirmation of a copy. */
  report(message: StatusMessage): void {
    this.status.set(message);
  }

  /**
   * Runs a request: shows its progress message, counts it as running, then publishes its answer
   * if no newer call of the same operation has started meanwhile. Failures are reported in the
   * status bar.
   */
  private async run<T>(
    operation: string,
    work: () => Promise<T>,
    handlers: { progress?: StatusMessage; onSuccess: (result: T) => void },
  ): Promise<void> {
    const call = this.nextCall(operation);
    this.beginTask(handlers.progress);
    try {
      const result = await work();
      if (this.isLatest(operation, call)) {
        handlers.onSuccess(result);
      }
    } catch (e) {
      if (this.isLatest(operation, call)) {
        this.status.set(fromError(e));
      }
    } finally {
      this.endTask();
    }
  }

  private nextCall(operation: string): number {
    const call = (this.calls.get(operation) ?? 0) + 1;
    this.calls.set(operation, call);
    return call;
  }

  private isLatest(operation: string, call: number): boolean {
    return this.calls.get(operation) === call;
  }

  private beginTask(progress?: StatusMessage) {
    this.runningTasks++;
    this.busy.set(true);
    if (progress) {
      this.status.set(progress);
    }
  }

  private endTask() {
    this.runningTasks--;
    this.busy.set(this.runningTasks > 0);
  }
}

/** Keeps the reference when it is one of the samples, otherwise takes the first sample. */
export function referenceAmong(samples: readonly string[], preferred: string): string {
  return samples.includes(preferred) ? preferred : (samples[0] ?? '');
}

/**
 * Language and country of a report: the server prints it on US Letter for Canada, on A4
 * otherwise (France for French, the United Kingdom for English).
 */
export function reportLocale(language: 'en' | 'fr', paper: Paper): string {
  if (paper === 'LETTER') {
    return `${language}-CA`;
  }
  return language === 'fr' ? 'fr-FR' : 'en-GB';
}
