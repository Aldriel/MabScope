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

import type { Messages } from '.';

/**
 * Texts of the interface in English, the same as those of the desktop application
 * (messages_en.properties). Its shape is checked against the French catalog.
 */

/** Writes a number with one decimal (2.1). */
const oneDecimal = (value: number) =>
  value.toLocaleString('en-CA', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Agrees a noun with a number: "1 sample", "3 samples". */
const plural = (count: number, one: string, many: string) =>
  count === 1 ? `${count} ${one}` : `${count} ${many}`;

export const en: Messages = {
  locale: 'en',
  languageName: 'English',

  menu: {
    file: 'File',
    newAnalysis: 'New analysis',
    demo: 'Load the demonstration',
    examples: 'Example antibodies',
    import: 'Import LC-MS results…',
    report: 'Download the QC report',
    options: 'Options',
    language: 'Language',
    textSize: 'Text size',
    textSizes: ['Small', 'Normal', 'Large', 'Extra large'],
    zoomIn: 'Larger text',
    zoomOut: 'Smaller text',
    zoomReset: 'Default text size',
    help: 'Help',
    guide: 'User guide',
    shortcuts: 'Keyboard shortcuts',
    imgt: 'IMGT, the international ImMunoGeneTics information system',
    abysis: 'CDR definitions (Andrew Martin’s group)',
    proforma: 'ProForma notation (PSI)',
    about: 'About MabScope',
  },

  tab: {
    antibody: 'Antibody',
    peptideMap: 'Peptide map',
    quantification: 'LC-MS quantification',
    report: 'QC report',
  },

  antibody: {
    name: 'Name',
    namePrompt: 'e.g. Trastuzumab',
    scheme: 'CDR definition',
    analyze: 'Analyze',
    examples: 'Examples:',
    demo: 'Demonstration',
    heavy: 'Heavy chain',
    heavyPrompt: 'Paste the heavy chain: residues or FASTA. Numbers and spaces are ignored.',
    light: 'Light chain',
    lightPrompt: 'Paste the light chain: residues or FASTA. Numbers and spaces are ignored.',
    placeholder: 'Paste the sequences above and click “Analyze”, or try an example.',
    sequences: 'Annotated sequences',
    regions: 'Regions',
    liabilities: 'Liabilities',
    cdrOnly: 'In the CDRs only',
    summary: (name, scheme, total, high, medium, inCdr) =>
      `${name} (${scheme} CDRs): ${total} liabilities, ${high} high risk, ${medium} medium ` +
      `risk, ${inCdr} in the CDRs.`,
    chainTitle: (name, type, length) => `${name} (${type}, ${length} residues)`,
    siteTooltip: (site, type, motif, risk) => `${site}: ${type} (${motif}), ${risk} risk`,
    legend:
      'CDRs in teal, constant domains in grey; liability sites are underlined in the color of ' +
      'their risk (red: high, orange: medium, yellow: low). Hover a site for details.',
  },

  map: {
    protease: 'Protease',
    missedCleavages: 'Missed cleavages',
    length: 'Length',
    minLength: 'Minimum length',
    maxLength: 'Maximum length',
    alkylated: 'Alkylated cysteines',
    digest: 'Digest',
    liabilitiesOnly: 'Peptides with a liability only',
    placeholder: 'Analyze an antibody in the “Antibody” tab to see its peptide map.',
    forms: 'Modified forms of the selected peptide',
    formsPlaceholder: 'Select a peptide to see the masses of its modified forms.',
    summary: (peptides, withLiability, coveringCdr) =>
      `${peptides} peptides, ${withLiability} with a liability, ${coveringCdr} covering a CDR`,
    copyProForma: 'Copy the ProForma sequence',
    copied: (proForma) => `Sequence copied: ${proForma}`,
  },

  quant: {
    import: 'Import results…',
    format: 'Format',
    clear: 'Remove the results',
    issues: 'Reading issues',
    comparison: 'Lot comparison',
    sites: 'Modification levels',
    coverage: 'Sequence coverage',
    reference: 'Reference',
    threshold: 'Threshold (points)',
    placeholder: 'Import the results of MaxQuant, FragPipe or Skyline, or load the demonstration.',
    empty:
      'No LC-MS results. Import a result file, or load the demonstration (three lots of ' +
      'trastuzumab).',
    summary: (source, format, peptides, sampleCount, samples, issues) =>
      `${source} (${format}): ${peptides} peptides in ` +
      `${plural(sampleCount, 'sample', 'samples')} (${samples}), ` +
      (issues === 0
        ? 'no reading issue.'
        : `${plural(issues, 'reading issue', 'reading issues')}.`),
    demoSource: 'Demonstration data',
    needsAnalysis: 'Analyze the antibody to quantify its sites.',
    issue: (severity, location, message) => `${severity} – ${location}: ${message}`,
    unmatched: (count, sequences) =>
      `${count} observed sequence(s) found in no chain: ${sequences}`,
    referenceColumn: (sample) => `${sample} (reference)`,
    flagged: 'To review',
    comparable: 'Comparable',
    comparisonSummary: (flagged, total) =>
      `${flagged} of ${total} forms differ from the reference by more than the threshold.`,
    comparisonPlaceholder:
      'The comparison appears once the antibody is analyzed and results are imported.',
  },

  report: {
    intro:
      'The quality control report gathers the CDRs, the liabilities and, when available, the ' +
      'peptide map, the LC-MS results and the lot comparison, in a PDF ready to share.',
    language: 'Language',
    paper: 'Paper size',
    analyst: 'Analyst',
    analystPrompt: 'optional',
    notes: 'Notes',
    notesPrompt: 'optional: batch numbers, method, conclusions…',
    sections: 'Sections',
    always: 'Always included: chains and CDRs, liabilities.',
    includePeptideMap: 'Theoretical peptide map',
    includeQuantification: 'LC-MS results and lot comparison',
    download: 'Download the PDF report',
    needsAnalysis: 'Analyze an antibody first.',
    ready: (name) => `Report of ${name}, saved in your downloads.`,
  },

  column: {
    chain: 'Chain',
    region: 'Region',
    positions: 'Positions',
    length: 'Length',
    sequence: 'Sequence',
    site: 'Site',
    type: 'Type',
    motif: 'Motif',
    risk: 'Risk',
    peptide: 'Peptide',
    missed: 'MC',
    mass: 'Mass (Da)',
    regions: 'Regions',
    liabilities: 'Liabilities',
    modification: 'Modification',
    delta: 'Δ mass (Da)',
    proForma: 'ProForma',
    sample: 'Sample',
    modified: 'Modified',
    forms: 'Forms',
    residues: 'Residues',
    coverage: 'Coverage',
    form: 'Form',
    deviation: 'Max. deviation',
    status: 'Status',
  },

  tip: {
    tabHelp: 'Help on this tab (F1)',
    scheme:
      'Definition of the CDR boundaries. Kabat: based on sequence variability. Chothia: based ' +
      'on the structural loops. AbM: compromise used by Oxford Molecular’s AbM software.',
    examples: 'Fill in the sequences of a therapeutic antibody and analyze them.',
    demo:
      'Loads trastuzumab and simulated peptide mapping results of three samples: two lots and a ' +
      'sample stressed at 40 °C.',
    cdrOnly: 'Shows only the sites inside the CDRs, where a modification can change the binding.',
    riskFilter: 'Minimum risk of the sites shown.',
    missedCleavages: 'Maximum number of internal cleavage sites left uncleaved in a peptide.',
    alkylated:
      'Cysteines reduced and alkylated with iodoacetamide (+57.021 Da), as in most peptide ' +
      'mapping protocols.',
    import:
      'Reads MaxQuant evidence.txt, FragPipe psm.tsv, a Skyline report or a generic table ' +
      '(sequence in ProForma, sample, intensity).',
    format: 'Automatic: the format is recognized from the columns of the file.',
    reference: 'Sample the other samples are compared with, usually the reference standard.',
    threshold: 'Largest acceptable difference with the reference, in percentage points.',
    paper: 'US Letter in North America, A4 elsewhere.',
  },

  scheme: { KABAT: 'Kabat', CHOTHIA: 'Chothia', ABM: 'AbM' },
  chainType: { HEAVY: 'heavy', LIGHT: 'light' },
  riskFilter: {
    LOW: 'All risks',
    MEDIUM: 'Medium and high risk',
    HIGH: 'High risk only',
  },
  type: {
    DEAMIDATION: 'Deamidation',
    ISOMERIZATION: 'Isomerization',
    OXIDATION: 'Oxidation',
    N_GLYCOSYLATION: 'N-glycosylation',
    UNPAIRED_CYSTEINE: 'Unpaired cysteine',
    PYROGLUTAMATE: 'Pyroglutamate',
    C_TERMINAL_LYSINE: 'C-terminal lysine',
    ASP_PRO_CLEAVAGE: 'Asp-Pro cleavage',
  },
  risk: { HIGH: 'High', MEDIUM: 'Medium', LOW: 'Low' },
  region: {
    FR1: 'FR1',
    CDR1: 'CDR1',
    FR2: 'FR2',
    CDR2: 'CDR2',
    FR3: 'FR3',
    CDR3: 'CDR3',
    FR4: 'FR4',
    CONSTANT: 'Constant',
  },
  protease: {
    TRYPSIN: 'Trypsin',
    TRYPSIN_P: 'Trypsin/P (also cleaves before P)',
    LYS_C: 'Lys-C',
    ARG_C: 'Arg-C',
    GLU_C: 'Glu-C',
    ASP_N: 'Asp-N (cleaves before D)',
    CHYMOTRYPSIN: 'Chymotrypsin',
  },
  format: {
    AUTO: 'Automatic',
    MAXQUANT: 'MaxQuant (evidence.txt)',
    FRAGPIPE: 'FragPipe (psm.tsv)',
    SKYLINE: 'Skyline (report)',
    GENERIC: 'Generic table (CSV)',
  },
  issue: { ERROR: 'ERROR', WARNING: 'WARNING' },
  reportLanguage: { en: 'English', fr: 'Français' },
  paper: { LETTER: 'US Letter (North America)', A4: 'A4' },

  percent: (value) => `${oneDecimal(value)}%`,
  points: (value) => `${oneDecimal(value)} pts`,

  status: {
    ready:
      'Ready. Paste the sequences of an antibody, or press Ctrl+D for the demonstration. F1 ' +
      'opens the help.',
    analyzing: (name) => `Analyzing ${name}…`,
    analyzed: (name, total, high) => `${name} analyzed: ${total} liabilities, ${high} high risk.`,
    loadingDemo: 'Loading the demonstration…',
    demoLoaded: (samples) =>
      `Demonstration loaded: trastuzumab and ${samples} samples compared with Lot A.`,
    digesting: 'Digesting…',
    digested: (peptides) => `Peptide map: ${peptides} peptides.`,
    importing: (file) => `Reading ${file}…`,
    imported: (peptides, samples) =>
      `${peptides} peptides read in ${plural(samples, 'sample', 'samples')}.`,
    importedWithIssues: (peptides, samples, issues) =>
      `${peptides} peptides read in ${plural(samples, 'sample', 'samples')}, ` +
      `${plural(issues, 'reading issue', 'reading issues')} (see the tab).`,
    comparing: 'Comparing the lots…',
    compared: (flagged, total) => `Comparison: ${flagged} of ${total} forms to review.`,
    writingReport: 'Writing the report…',
    reportSaved: (file) => `Report ${file} downloaded.`,
    error: (text) => `Error: ${text}`,
    errorDetail: (explanation, detail) => `${explanation} (${detail})`,
  },

  error: {
    notAnAntibody: 'a sequence is not an antibody chain of its type',
    parsing: 'invalid data',
    invalidInput: 'invalid input',
    notFound: 'service not found',
    tooLarge: 'file too large',
    unexpected: 'unexpected error',
    server: 'the server does not answer; try again in a moment',
    noAnalysis: 'analyze an antibody first.',
    lengths: 'the minimum peptide length exceeds the maximum length.',
  },

  dialog: { close: 'Close' },

  help: {
    title: 'User guide',
    searchLabel: 'Search:',
    searchPrompt: 'keyword, e.g. deamidation, MaxQuant, threshold…',
    clear: 'Clear',
    results: (sections, occurrences) =>
      `${sections === 0 ? 'no sections' : plural(sections, 'section', 'sections')} · ` +
      (occurrences === 0 ? 'no occurrences' : plural(occurrences, 'occurrence', 'occurrences')),
    none: (query) => `No section of the guide contains “${query}”.`,
  },

  about: {
    title: (app) => `About ${app}`,
    version: (version) => `Version ${version}`,
    description:
      'Therapeutic antibody characterization: CDR detection, liability hotspots, theoretical ' +
      'peptide map, quantification of the modifications measured by LC-MS, lot comparison and ' +
      'PDF quality control report.',
    author: (name) => `Developed by ${name}, bioinformatics consultant`,
    website: 'https://www.maximeethier.com/en',
    sources:
      'CDR definitions: Kabat, Chothia and AbM, located with the rules of Andrew Martin. ' +
      'Modification masses: Unimod.',
    references:
      'Peptide notation: ProForma 2.0 (HUPO-PSI). Example sequences: trastuzumab and ' +
      'adalimumab (DrugBank, IMGT/mAb-DB).',
    disclaimer: 'For research only. The results must be confirmed by validated analytical methods.',
    runtime: (react) => `React ${react} · calculations on the Java server`,
  },

  shortcuts: {
    keys: 'Keys',
    action: 'Action',
    tabs: 'Show tab 1 to 4',
    enter: 'Analyze the sequences (in the name)',
    helpSearch: 'Search the guide',
    closeHelp: 'Clear the search, then close the guide',
  },
};
