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
 * Journeys of a visitor through the real application and REST API: the example antibodies with
 * every CDR definition, the peptide map, the import of result files, the lot comparison, the PDF
 * report, the guide and the menus.
 */

import { readFileSync } from 'node:fs';
import { SCHEMES } from '../src/model/types';
import { expect, test } from './app';

/** Simulated results of three lots of trastuzumab, also used by the demonstration. */
const DEMO_CSV = readFileSync(
  new URL(
    '../../core/src/main/resources/com/bioinfo/mabscope/examples/trastuzumab-lots-demo.csv',
    import.meta.url,
  ),
  'utf8',
);

/** Small MaxQuant evidence.txt: two lots, a decoy and a modification MaxQuant does not know. */
const MAXQUANT = [
  'Modified sequence\tExperiment\tIntensity\tReverse\tPotential contaminant',
  '_IYPTNGYTR_\tLot 1\t900000\t\t',
  '_IYPTN(Deamidation (NQ))GYTR_\tLot 1\t10000\t\t',
  '_IYPTNGYTR_\tLot 2\t800000\t\t',
  '_IYPTN(Deamidation (NQ))GYTR_\tLot 2\t90000\t\t',
  '_RTYGNTPYI_\tLot 2\t5000\t+\t',
  '_IYPTN(Unknown mod)GYTR_\tLot 2\t5000\t\t',
].join('\n');

test('an example antibody is analyzed with each CDR definition', async ({ app }) => {
  await app.open();
  const tab = await app.analyzeExample('Trastuzumab');

  for (const scheme of SCHEMES) {
    await tab.locator('#antibody-scheme').selectOption(scheme);
    await tab.getByRole('button', { name: app.m.antibody.analyze, exact: true }).click();
    await app.settle();
    await expect(tab.locator('.summary')).toContainText(app.m.scheme[scheme]);
    // Three CDRs on each chain.
    await expect(app.rows('.region-table').and(app.page.locator('.cdr'))).toHaveCount(6);
  }
  await expect(tab.locator('.sequence-view .liability-site').first()).toBeVisible();
});

test('the liabilities are filtered by region and risk', async ({ app }) => {
  await app.open();
  const tab = await app.analyzeExample('Adalimumab');
  const liabilities = app.rows('.liability-table');
  const all = await liabilities.count();
  expect(all).toBeGreaterThan(5);

  await tab.getByLabel(app.m.antibody.cdrOnly).check();
  const inCdr = await liabilities.count();
  expect(inCdr).toBeLessThan(all);
  await tab.locator('#risk-filter').selectOption('HIGH');
  expect(await liabilities.count()).toBeLessThanOrEqual(inCdr);
  for (const risk of await tab.locator('.liability-table .risk').all()) {
    await expect(risk).toHaveClass(/risk-high/);
  }
});

test('a sequence that is not an antibody is reported', async ({ app }) => {
  test.info().annotations.push({ type: 'expects-error' });
  await app.open();
  const tab = await app.show('antibody');
  await tab.locator('#antibody-heavy').fill('PEPTIDE');
  await tab.getByRole('button', { name: app.m.antibody.analyze, exact: true }).click();
  await app.settle();

  await expect(app.page.locator('.status-text.error')).toContainText(app.m.error.notAnAntibody);
});

test('the peptide map lists the modified forms and is digested again', async ({ app }) => {
  await app.open();
  await app.analyzeExample('Trastuzumab');
  const tab = await app.show('peptideMap');

  await expect(app.rows('.peptide-table')).not.toHaveCount(0);
  await app.rows('.peptide-table').and(app.page.locator('.has-liability')).first().click();
  await expect(app.rows('.form-table')).not.toHaveCount(0);
  await tab.getByRole('button', { name: app.m.map.copyProForma }).click();
  await expect(app.status()).toContainText(app.m.map.copied(''));

  const before = await app.rows('.peptide-table').count();
  await tab.locator('#protease').selectOption('LYS_C');
  await tab.locator('#missed-cleavages').fill('0');
  await tab.getByRole('button', { name: app.m.map.digest, exact: true }).click();
  await app.settle();
  await expect(app.status()).toContainText(/\d+/);
  expect(await app.rows('.peptide-table').count()).not.toBe(before);
});

test('the demonstration compares three lots with an adjustable reference', async ({ app }) => {
  await app.open();
  await app.menu(app.m.menu.file, app.m.menu.demo);
  await app.settle();
  const tab = app.panel();

  await expect(tab.locator('.import-summary')).toContainText(app.m.quant.demoSource);
  await expect(
    tab.getByRole('columnheader', { name: app.m.quant.referenceColumn('Lot A') }),
  ).toBeVisible();
  await expect(app.rows('.comparison-table').and(app.page.locator('.flagged'))).not.toHaveCount(0);

  await tab.locator('#reference').selectOption('Lot B');
  await app.settle();
  await expect(
    tab.getByRole('columnheader', { name: app.m.quant.referenceColumn('Lot B') }),
  ).toBeVisible();

  await tab.locator('#threshold').fill('100');
  await tab.locator('#threshold').press('Enter');
  await app.settle();
  await expect(app.rows('.comparison-table').and(app.page.locator('.flagged'))).toHaveCount(0);

  await tab.getByRole('tab', { name: app.m.quant.sites }).click();
  await expect(app.rows('.site-table')).not.toHaveCount(0);
  await tab.getByRole('tab', { name: app.m.quant.coverage }).click();
  await expect(app.rows('.coverage-table')).not.toHaveCount(0);
});

test('a generic CSV file is imported and quantified', async ({ app }) => {
  await app.open();
  await app.analyzeExample('Trastuzumab');
  const tab = await app.importResults('lots.csv', DEMO_CSV);

  await expect(tab.locator('.import-summary')).toContainText('lots.csv');
  await expect(tab.locator('.import-summary')).toContainText(app.m.format.GENERIC);
  await expect(app.rows('.comparison-table')).not.toHaveCount(0);
});

test('a MaxQuant file is recognized, with its reading issues', async ({ app }) => {
  await app.open();
  await app.analyzeExample('Trastuzumab');
  const tab = await app.importResults('evidence.txt', MAXQUANT);

  await expect(tab.locator('.import-summary')).toContainText(app.m.format.MAXQUANT);
  await expect(tab.locator('details.issues')).toBeVisible();
  await expect(
    tab.getByRole('columnheader', { name: app.m.quant.referenceColumn('Lot 1') }),
  ).toBeVisible();
  await expect(app.rows('.comparison-table').first()).toContainText('N55');
});

test('the QC report is downloaded as a PDF', async ({ app }) => {
  await app.open();
  await app.menu(app.m.menu.file, app.m.menu.demo);
  await app.settle();
  const tab = await app.show('report');
  await tab.locator('#report-analyst').fill('E2E');
  await tab.locator('#report-paper').selectOption('A4');

  const [download] = await Promise.all([
    app.page.waitForEvent('download'),
    tab.getByRole('button', { name: app.m.report.download }).click(),
  ]);

  expect(download.suggestedFilename()).toMatch(/\.pdf$/);
  const chunks: Buffer[] = [];
  for await (const chunk of await download.createReadStream()) {
    chunks.push(chunk as Buffer);
  }
  const pdf = Buffer.concat(chunks);
  expect(pdf.subarray(0, 5).toString()).toBe('%PDF-');
  expect(pdf.length).toBeGreaterThan(10_000);
  await expect(app.status()).toContainText(download.suggestedFilename());
});

test('the guide opens at the section of the tab and is searched', async ({ app }) => {
  await app.open();
  await app.show('quantification');
  await app.page.keyboard.press('F1');

  const guide = app.page.locator('.help-panel');
  await expect(guide.locator('.help-title')).toBeVisible();
  await expect(guide.locator('.help-image img').first()).toBeVisible();
  await app.page.keyboard.type('MaxQuant');
  await expect(guide.locator('mark').first()).toBeVisible();
  await app.page.keyboard.press('Escape');
  await app.page.keyboard.press('Escape');
  await expect(guide).toBeHidden();
});

test('the language and the text size are changed from the menus', async ({ app }) => {
  await app.open();
  const other = app.language === 'fr' ? 'en' : 'fr';
  const m =
    other === 'fr' ? (await import('../src/i18n/fr')).fr : (await import('../src/i18n/en')).en;

  await app.menu(app.m.menu.options, app.m.menu.textSize, app.m.menu.textSizes[2]);
  await expect(app.page.locator('html')).toHaveCSS('font-size', '16px');
  await app.menu(app.m.menu.options, app.m.menu.language, m.languageName);

  await expect(app.page.getByRole('tab', { name: m.tab.antibody })).toBeVisible();
  await expect(app.page.locator('html')).toHaveAttribute('lang', other);
});
