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
 * Data exchanged with the REST API: the JSON bodies of the requests (Requests.java) and of the
 * responses (Views.java) of the server module. Enumerations are sent by their Java name.
 */

/** Definitions of the CDR boundaries. */
export const SCHEMES = ['KABAT', 'CHOTHIA', 'ABM'] as const;
export type NumberingScheme = (typeof SCHEMES)[number];

export type ChainType = 'HEAVY' | 'LIGHT';

/** Regions of a chain, from the N-terminus. */
export const REGIONS = ['FR1', 'CDR1', 'FR2', 'CDR2', 'FR3', 'CDR3', 'FR4', 'CONSTANT'] as const;
export type Region = (typeof REGIONS)[number];

export const LIABILITY_TYPES = [
  'DEAMIDATION',
  'ISOMERIZATION',
  'OXIDATION',
  'N_GLYCOSYLATION',
  'UNPAIRED_CYSTEINE',
  'PYROGLUTAMATE',
  'C_TERMINAL_LYSINE',
  'ASP_PRO_CLEAVAGE',
] as const;
export type LiabilityType = (typeof LIABILITY_TYPES)[number];

/** Risk levels, from the lowest. */
export const RISKS = ['LOW', 'MEDIUM', 'HIGH'] as const;
export type Risk = (typeof RISKS)[number];

/** Enzymes of the peptide map, in the order of the list. */
export const PROTEASES = [
  'TRYPSIN',
  'TRYPSIN_P',
  'LYS_C',
  'ARG_C',
  'GLU_C',
  'ASP_N',
  'CHYMOTRYPSIN',
] as const;
export type Protease = (typeof PROTEASES)[number];

/** Result files of MS software that the server reads. */
export const MS_FORMATS = ['MAXQUANT', 'FRAGPIPE', 'SKYLINE', 'GENERIC'] as const;
export type MsFormat = (typeof MS_FORMATS)[number];

/** Category of an error answered by the server, translated for the user. */
export type ErrorKind =
  'PARSING' | 'INVALID_INPUT' | 'NOT_AN_ANTIBODY' | 'NOT_FOUND' | 'TOO_LARGE' | 'UNEXPECTED';

// ----- Requests -----

/** Antibody sent to the server; sequences may be pasted as FASTA, an empty chain is omitted. */
export interface AntibodyRequest {
  name: string;
  heavy: string;
  light: string;
  scheme: NumberingScheme;
}

/** Digestion of the peptide map. */
export interface DigestionSettings {
  protease: Protease;
  missedCleavages: number;
  minLength: number;
  maxLength: number;
  alkylatedCysteines: boolean;
}

/** Peptide measured in a sample, in ProForma notation. */
export interface Observation {
  sequence: string;
  sample: string;
  intensity: number;
}

/** Body of POST /api/report. */
export interface ReportRequest {
  antibody: AntibodyRequest;
  /** Language and country of the reader, such as "fr-CA"; the country sets the paper size. */
  locale: string;
  timeZone: string;
  analyst: string;
  notes: string;
  /** Includes the peptide map when present. */
  digestion: DigestionSettings | null;
  /** Includes the LC-MS results when not empty. */
  peptides: Observation[];
  /** Includes the lot comparison when not blank. */
  reference: string;
  threshold: number;
}

// ----- Responses -----

/** A liability site. */
export interface Liability {
  chain: string;
  type: LiabilityType;
  /** 1-based position of the modified residue. */
  position: number;
  motif: string;
  region: Region;
  risk: Risk;
  inCdr: boolean;
}

/** A region of a chain, with its 1-based positions. */
export interface RegionSpan {
  region: Region;
  start: number;
  end: number;
  cdr: boolean;
  sequence: string;
}

export interface ChainView {
  name: string;
  type: ChainType;
  sequence: string;
  length: number;
  regions: RegionSpan[];
}

/** Response of POST /api/analysis. */
export interface Analysis {
  name: string;
  scheme: NumberingScheme;
  chains: ChainView[];
  liabilities: Liability[];
}

/** A peptide carrying one modification at a liability site. */
export interface ModifiedForm {
  type: LiabilityType;
  position: number;
  modification: string;
  proForma: string;
  monoisotopicMass: number;
}

/** A peptide of the theoretical map, as returned by POST /api/peptide-map. */
export interface Peptide {
  chain: string;
  start: number;
  end: number;
  sequence: string;
  missedCleavages: number;
  proForma: string;
  monoisotopicMass: number;
  regions: Region[];
  coversCdr: boolean;
  liabilities: Liability[];
  forms: ModifiedForm[];
}

/** A row of a result file that could not be read. */
export interface ParseIssue {
  severity: 'WARNING' | 'ERROR';
  location: string;
  message: string;
}

/** Response of POST /api/ms/import and GET /api/examples/demo-results. */
export interface MsImport {
  format: MsFormat;
  samples: string[];
  peptides: Observation[];
  issues: ParseIssue[];
}

/** Coverage of a chain in a sample. */
export interface Coverage {
  chain: string;
  sample: string;
  length: number;
  coveredResidues: number;
  percent: number;
}

/** Modification of a site in a sample. */
export interface SiteQuantity {
  site: Liability;
  sample: string;
  totalIntensity: number;
  /** Percentage of each modified form, by name. */
  percents: Record<string, number>;
  modifiedPercent: number;
}

/** One modified form of a site, compared between lots. */
export interface SiteComparison {
  site: Liability;
  form: string;
  percents: Record<string, number>;
  referencePercent: number;
  maxDeviation: number;
  flagged: boolean;
}

export interface Comparison {
  reference: string;
  threshold: number;
  rows: SiteComparison[];
}

/** Response of POST /api/quantify. */
export interface Quantification {
  samples: string[];
  coverage: Coverage[];
  sites: SiteQuantity[];
  unmatchedSequences: string[];
  comparison: Comparison | null;
}

/** An example antibody, from GET /api/examples. */
export interface Example {
  id: string;
  name: string;
  heavy: string;
  light: string;
}

/** A PDF report and the name under which to save it. */
export interface ReportFile {
  content: Blob;
  fileName: string;
}
