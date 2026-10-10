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
 * Textes de l’interface en français, les mêmes que ceux de l’application de bureau
 * (messages_fr.properties). Ce catalogue définit les clés : le catalogue anglais doit avoir
 * exactement la même forme. Utiliser l’apostrophe typographique (’).
 */

/** Écrit un nombre avec une décimale, à la française (2,1). */
const oneDecimal = (value: number) =>
  value.toLocaleString('fr-CA', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** Accorde un nom avec un nombre : « 1 échantillon », « 3 échantillons ». */
const plural = (count: number, one: string, many: string) =>
  count <= 1 ? `${count} ${one}` : `${count} ${many}`;

export const fr = {
  locale: 'fr',
  languageName: 'Français',

  menu: {
    file: 'Fichier',
    newAnalysis: 'Nouvelle analyse',
    demo: 'Charger la démonstration',
    examples: 'Anticorps d’exemple',
    import: 'Importer des résultats LC-MS…',
    report: 'Télécharger le rapport CQ',
    options: 'Options',
    language: 'Langue',
    textSize: 'Taille du texte',
    textSizes: ['Petite', 'Normale', 'Grande', 'Très grande'],
    zoomIn: 'Agrandir le texte',
    zoomOut: 'Réduire le texte',
    zoomReset: 'Taille de texte par défaut',
    help: 'Aide',
    guide: 'Guide d’utilisation',
    shortcuts: 'Raccourcis clavier',
    imgt: 'IMGT, système d’information international ImMunoGeneTics',
    abysis: 'Définitions des CDR (groupe d’Andrew Martin)',
    proforma: 'Notation ProForma (PSI)',
    about: 'À propos de MabScope',
  },

  tab: {
    antibody: 'Anticorps',
    peptideMap: 'Carte peptidique',
    quantification: 'Quantification LC-MS',
    report: 'Rapport CQ',
  },

  antibody: {
    name: 'Nom',
    namePrompt: 'ex. Trastuzumab',
    scheme: 'Définition des CDR',
    analyze: 'Analyser',
    examples: 'Exemples :',
    demo: 'Démonstration',
    heavy: 'Chaîne lourde',
    heavyPrompt:
      'Collez la chaîne lourde : résidus ou FASTA. Les chiffres et les espaces sont ignorés.',
    light: 'Chaîne légère',
    lightPrompt:
      'Collez la chaîne légère : résidus ou FASTA. Les chiffres et les espaces sont ignorés.',
    placeholder:
      'Collez les séquences ci-dessus et cliquez sur « Analyser », ou essayez un exemple.',
    sequences: 'Séquences annotées',
    regions: 'Régions',
    liabilities: 'Zones à risque',
    cdrOnly: 'Dans les CDR seulement',
    summary: (
      name: string,
      scheme: string,
      total: number,
      high: number,
      medium: number,
      inCdr: number,
    ) =>
      `${name} (CDR ${scheme}) : ${total} zones à risque, ${high} à risque élevé, ` +
      `${medium} à risque moyen, ${inCdr} dans les CDR.`,
    chainTitle: (name: string, type: string, length: number) =>
      `${name} (${type}, ${length} résidus)`,
    siteTooltip: (site: string, type: string, motif: string, risk: string) =>
      `${site} : ${type} (${motif}), risque ${risk}`,
    legend:
      'CDR en sarcelle, domaines constants en gris ; les zones à risque sont soulignées de la ' +
      'couleur de leur risque (rouge : élevé, orange : moyen, jaune : faible). Survolez un site ' +
      'pour le détail.',
  },

  map: {
    protease: 'Protéase',
    missedCleavages: 'Clivages manqués',
    length: 'Longueur',
    minLength: 'Longueur minimale',
    maxLength: 'Longueur maximale',
    alkylated: 'Cystéines alkylées',
    digest: 'Digérer',
    liabilitiesOnly: 'Peptides avec une zone à risque seulement',
    placeholder: 'Analysez un anticorps dans l’onglet « Anticorps » pour voir sa carte peptidique.',
    forms: 'Formes modifiées du peptide sélectionné',
    formsPlaceholder: 'Sélectionnez un peptide pour voir les masses de ses formes modifiées.',
    summary: (peptides: number, withLiability: number, coveringCdr: number) =>
      `${peptides} peptides, ${withLiability} avec une zone à risque, ${coveringCdr} couvrant ` +
      'une CDR',
    copyProForma: 'Copier la séquence ProForma',
    copied: (proForma: string) => `Séquence copiée : ${proForma}`,
  },

  quant: {
    import: 'Importer des résultats…',
    format: 'Format',
    clear: 'Retirer les résultats',
    issues: 'Problèmes de lecture',
    comparison: 'Comparaison de lots',
    sites: 'Niveaux de modification',
    coverage: 'Couverture de séquence',
    reference: 'Référence',
    threshold: 'Seuil (points)',
    placeholder:
      'Importez les résultats de MaxQuant, FragPipe ou Skyline, ou chargez la démonstration.',
    empty:
      'Aucun résultat LC-MS. Importez un fichier de résultats, ou chargez la démonstration ' +
      '(trois lots de trastuzumab).',
    summary: (
      source: string,
      format: string,
      peptides: number,
      sampleCount: number,
      samples: string,
      issues: number,
    ) =>
      `${source} (${format}) : ${peptides} peptides dans ` +
      `${plural(sampleCount, 'échantillon', 'échantillons')} (${samples}), ` +
      (issues === 0
        ? 'aucun problème de lecture.'
        : `${plural(issues, 'problème de lecture', 'problèmes de lecture')}.`),
    demoSource: 'Données de démonstration',
    needsAnalysis: 'Analysez l’anticorps pour quantifier ses sites.',
    issue: (severity: string, location: string, message: string) =>
      `${severity} – ${location} : ${message}`,
    unmatched: (count: number, sequences: string) =>
      `${count} séquence(s) observée(s) absente(s) des chaînes : ${sequences}`,
    referenceColumn: (sample: string) => `${sample} (référence)`,
    flagged: 'À examiner',
    comparable: 'Comparable',
    comparisonSummary: (flagged: number, total: number) =>
      `${flagged} formes sur ${total} s’écartent de la référence de plus que le seuil.`,
    comparisonPlaceholder:
      'La comparaison apparaît une fois l’anticorps analysé et des résultats importés.',
  },

  report: {
    intro:
      'Le rapport de contrôle qualité réunit les CDR, les zones à risque et, s’ils sont ' +
      'disponibles, la carte peptidique, les résultats LC-MS et la comparaison de lots, dans un ' +
      'PDF prêt à partager.',
    language: 'Langue',
    paper: 'Format du papier',
    analyst: 'Analyste',
    analystPrompt: 'facultatif',
    notes: 'Notes',
    notesPrompt: 'facultatif : numéros de lot, méthode, conclusions…',
    sections: 'Sections',
    always: 'Toujours incluses : chaînes et CDR, zones à risque.',
    includePeptideMap: 'Carte peptidique théorique',
    includeQuantification: 'Résultats LC-MS et comparaison de lots',
    download: 'Télécharger le rapport PDF',
    needsAnalysis: 'Analysez d’abord un anticorps.',
    ready: (name: string) => `Rapport de ${name}, enregistré dans vos téléchargements.`,
  },

  column: {
    chain: 'Chaîne',
    region: 'Région',
    positions: 'Positions',
    length: 'Longueur',
    sequence: 'Séquence',
    site: 'Site',
    type: 'Type',
    motif: 'Motif',
    risk: 'Risque',
    peptide: 'Peptide',
    missed: 'CM',
    mass: 'Masse (Da)',
    regions: 'Régions',
    liabilities: 'Zones à risque',
    modification: 'Modification',
    delta: 'Δ masse (Da)',
    proForma: 'ProForma',
    sample: 'Échantillon',
    modified: 'Modifié',
    forms: 'Formes',
    residues: 'Résidus',
    coverage: 'Couverture',
    form: 'Forme',
    deviation: 'Écart max.',
    status: 'Statut',
  },

  tip: {
    tabHelp: 'Aide sur cet onglet (F1)',
    scheme:
      'Définition des limites des CDR. Kabat : d’après la variabilité des séquences. Chothia : ' +
      'd’après les boucles de la structure. AbM : compromis du logiciel AbM d’Oxford Molecular.',
    examples: 'Remplit les séquences d’un anticorps thérapeutique et les analyse.',
    demo:
      'Charge le trastuzumab et des résultats simulés de cartographie peptidique de trois ' +
      'échantillons : deux lots et un échantillon stressé à 40 °C.',
    cdrOnly:
      'N’affiche que les sites situés dans les CDR, où une modification peut changer la liaison ' +
      'à l’antigène.',
    riskFilter: 'Risque minimal des sites affichés.',
    missedCleavages: 'Nombre maximal de sites de coupure internes non coupés par peptide.',
    alkylated:
      'Cystéines réduites et alkylées à l’iodoacétamide (+57,021 Da), comme dans la plupart des ' +
      'protocoles de cartographie peptidique.',
    import:
      'Lit le fichier evidence.txt de MaxQuant, psm.tsv de FragPipe, un rapport Skyline ou un ' +
      'tableau générique (séquence en ProForma, échantillon, intensité).',
    format: 'Automatique : le format est reconnu d’après les colonnes du fichier.',
    reference: 'Échantillon auquel les autres sont comparés, en général l’étalon de référence.',
    threshold: 'Écart maximal accepté avec la référence, en points de pourcentage.',
    paper: 'Lettre US en Amérique du Nord, A4 ailleurs.',
  },

  scheme: { KABAT: 'Kabat', CHOTHIA: 'Chothia', ABM: 'AbM' },
  chainType: { HEAVY: 'lourde', LIGHT: 'légère' },
  riskFilter: {
    LOW: 'Tous les risques',
    MEDIUM: 'Risque moyen et élevé',
    HIGH: 'Risque élevé seulement',
  },
  type: {
    DEAMIDATION: 'Désamidation',
    ISOMERIZATION: 'Isomérisation',
    OXIDATION: 'Oxydation',
    N_GLYCOSYLATION: 'N-glycosylation',
    UNPAIRED_CYSTEINE: 'Cystéine non appariée',
    PYROGLUTAMATE: 'Pyroglutamate',
    C_TERMINAL_LYSINE: 'Lysine C-terminale',
    ASP_PRO_CLEAVAGE: 'Coupure Asp-Pro',
  },
  risk: { HIGH: 'Élevé', MEDIUM: 'Moyen', LOW: 'Faible' },
  region: {
    FR1: 'FR1',
    CDR1: 'CDR1',
    FR2: 'FR2',
    CDR2: 'CDR2',
    FR3: 'FR3',
    CDR3: 'CDR3',
    FR4: 'FR4',
    CONSTANT: 'Constante',
  },
  protease: {
    TRYPSIN: 'Trypsine',
    TRYPSIN_P: 'Trypsine/P (coupe aussi devant P)',
    LYS_C: 'Lys-C',
    ARG_C: 'Arg-C',
    GLU_C: 'Glu-C',
    ASP_N: 'Asp-N (coupe devant D)',
    CHYMOTRYPSIN: 'Chymotrypsine',
  },
  format: {
    AUTO: 'Automatique',
    MAXQUANT: 'MaxQuant (evidence.txt)',
    FRAGPIPE: 'FragPipe (psm.tsv)',
    SKYLINE: 'Skyline (rapport)',
    GENERIC: 'Tableau générique (CSV)',
  },
  issue: { ERROR: 'ERREUR', WARNING: 'AVERTISSEMENT' },
  reportLanguage: { en: 'English', fr: 'Français' },
  paper: { LETTER: 'Lettre US (Amérique du Nord)', A4: 'A4' },

  /** Pourcentage avec une décimale : « 2,1 % ». */
  percent: (value: number) => `${oneDecimal(value)} %`,
  /** Écart en points de pourcentage : « 2,1 pts ». */
  points: (value: number) => `${oneDecimal(value)} pts`,

  status: {
    ready:
      'Prêt. Collez les séquences d’un anticorps, ou appuyez sur Ctrl+D pour la démonstration. ' +
      'F1 ouvre l’aide.',
    analyzing: (name: string) => `Analyse de ${name}…`,
    analyzed: (name: string, total: number, high: number) =>
      `${name} analysé : ${total} zones à risque, ${high} à risque élevé.`,
    loadingDemo: 'Chargement de la démonstration…',
    demoLoaded: (samples: number) =>
      `Démonstration chargée : trastuzumab et ${samples} échantillons comparés au Lot A.`,
    digesting: 'Digestion…',
    digested: (peptides: number) => `Carte peptidique : ${peptides} peptides.`,
    importing: (file: string) => `Lecture de ${file}…`,
    imported: (peptides: number, samples: number) =>
      `${peptides} peptides lus dans ${plural(samples, 'échantillon', 'échantillons')}.`,
    importedWithIssues: (peptides: number, samples: number, issues: number) =>
      `${peptides} peptides lus dans ${plural(samples, 'échantillon', 'échantillons')}, ` +
      `${plural(issues, 'problème de lecture', 'problèmes de lecture')} (voir l’onglet).`,
    comparing: 'Comparaison des lots…',
    compared: (flagged: number, total: number) =>
      `Comparaison : ${flagged} formes sur ${total} à examiner.`,
    writingReport: 'Rédaction du rapport…',
    reportSaved: (file: string) => `Rapport ${file} téléchargé.`,
    error: (text: string) => `Erreur : ${text}`,
    errorDetail: (explanation: string, detail: string) => `${explanation} (${detail})`,
  },

  error: {
    notAnAntibody: 'une séquence n’est pas une chaîne d’anticorps de son type',
    parsing: 'données invalides',
    invalidInput: 'saisie invalide',
    notFound: 'service introuvable',
    tooLarge: 'fichier trop volumineux',
    unexpected: 'erreur inattendue',
    server: 'le serveur ne répond pas ; réessayez dans un instant',
    noAnalysis: 'analysez d’abord un anticorps.',
    lengths: 'la longueur minimale des peptides dépasse la longueur maximale.',
  },

  dialog: { close: 'Fermer' },

  help: {
    title: 'Guide d’utilisation',
    searchLabel: 'Rechercher :',
    searchPrompt: 'mot-clé, ex. désamidation, MaxQuant, seuil…',
    clear: 'Effacer',
    results: (sections: number, occurrences: number) =>
      `${sections === 0 ? 'aucune section' : plural(sections, 'section', 'sections')} · ` +
      (occurrences === 0 ? 'aucune occurrence' : plural(occurrences, 'occurrence', 'occurrences')),
    none: (query: string) => `Aucune section du guide ne contient « ${query} ».`,
  },

  about: {
    title: (app: string) => `À propos de ${app}`,
    version: (version: string) => `Version ${version}`,
    description:
      'Caractérisation des anticorps thérapeutiques : détection des CDR, zones à risque, carte ' +
      'peptidique théorique, quantification des modifications mesurées par LC-MS, comparaison ' +
      'de lots et rapport PDF de contrôle qualité.',
    author: (name: string) => `Développé par ${name}, consultant en bio-informatique`,
    website: 'https://www.maximeethier.com',
    sources:
      'Définitions des CDR : Kabat, Chothia et AbM, localisées avec les règles d’Andrew Martin. ' +
      'Masses des modifications : Unimod.',
    references:
      'Notation des peptides : ProForma 2.0 (HUPO-PSI). Séquences d’exemple : trastuzumab et ' +
      'adalimumab (DrugBank, IMGT/mAb-DB).',
    disclaimer:
      'Destiné à la recherche uniquement. Les résultats doivent être confirmés par des méthodes ' +
      'analytiques validées.',
    runtime: (react: string) => `React ${react} · calculs sur le serveur Java`,
  },

  shortcuts: {
    keys: 'Touches',
    action: 'Action',
    tabs: 'Afficher l’onglet 1 à 4',
    enter: 'Analyser les séquences (dans le nom)',
    helpSearch: 'Rechercher dans le guide',
    closeHelp: 'Effacer la recherche, puis fermer le guide',
  },
};
