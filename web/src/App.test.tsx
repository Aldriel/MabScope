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
 * Integration tests of the application: the views, their controllers and the model, with a fake
 * server. Each test uses the interface as a user would, with the mouse and the keyboard.
 */
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { DEFAULT_DIGESTION } from './model/AppModel';
import { LOCALE_KEY } from './model/settings';
import { DEMO, fakeApi, IMPORTED, PEPTIDES } from './test/fixtures';

let api: ReturnType<typeof fakeApi>;
let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  api = fakeApi();
  user = userEvent.setup();
  // user-event replaces the clipboard of the browser by its own.
  vi.spyOn(navigator.clipboard, 'writeText');
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
});

function renderApp(languages = ['en-CA']) {
  return render(<App api={api} storage={localStorage} languages={languages} />);
}

/** Field of the interface by its identifier. */
const field = <T extends HTMLElement = HTMLInputElement>(id: string) =>
  document.getElementById(id) as T;

const statusText = () => screen.getByRole('status').textContent;

/** Rows of a table, without its header. */
const rowsOf = (name: string) =>
  within(screen.getByRole('table', { name })).getAllByRole('row').slice(1);

/** Texts of the cells of a row. */
const cells = (row: HTMLElement) =>
  within(row)
    .getAllByRole('cell')
    .map((c) => c.textContent);

/** Title of the tab shown, among the main tabs. */
const activeTab = () => document.querySelector('.tab-bar:not(.inner) .tab.active')?.textContent;

/** Opens a menu of the menu bar, then follows the path of its items. */
async function chooseMenu(...path: string[]) {
  await user.click(screen.getByRole('menuitem', { name: path[0] }));
  for (const label of path.slice(1, -1)) {
    await user.click(screen.getByRole('menuitem', { name: new RegExp(`^${label}`) }));
  }
  const last = path[path.length - 1];
  // In jsdom, the pointer simulated by user-event closes a sub-menu when it moves over its items,
  // so the last item is clicked directly; the functional tests use the real menus.
  fireEvent.click(
    screen.queryByRole('menuitemradio', { name: last }) ??
      screen.getByRole('menuitem', { name: new RegExp(`^${last}`) }),
  );
  await act(() => Promise.resolve());
}

async function loadExample() {
  await user.click(screen.getByRole('button', { name: 'Trastuzumab' }));
  await screen.findByText(/Trastuzumab \(Kabat CDRs\)/);
}

describe('Antibody tab', () => {
  it('analyzes the sequences typed and lists their regions and liabilities', async () => {
    renderApp();
    expect(screen.getAllByText(/Paste the sequences above/).length).toBeGreaterThan(0);

    await user.type(field('antibody-name'), 'mAb');
    await user.type(field('antibody-heavy'), 'EVQL');
    await user.type(field('antibody-light'), 'DIQM');
    await user.selectOptions(field('antibody-scheme'), 'CHOTHIA');
    await user.click(screen.getByRole('button', { name: 'Analyze' }));

    expect(
      await screen.findByText(
        'mAb (Kabat CDRs): 4 liabilities, 1 high risk, 1 medium risk, 3 in the CDRs.',
      ),
    ).toBeTruthy();
    expect(api.analyze).toHaveBeenCalledWith({
      name: 'mAb',
      heavy: 'EVQL',
      light: 'DIQM',
      scheme: 'CHOTHIA',
    });
    expect(statusText()).toBe('mAb analyzed: 4 liabilities, 1 high risk.');

    const regions = rowsOf('Regions');
    expect(regions.map(cells)[1]).toEqual(['Heavy', 'CDR1', '5–10', '6', 'NGSGDY']);
    expect(regions[1].className).toBe('cdr');
    await user.click(within(regions[2]).getByText('FR2'));
    expect(rowsOf('Regions')[2].getAttribute('aria-selected')).toBe('true');

    expect(rowsOf('Liabilities').map((r) => cells(r)[1])).toEqual(['N5', 'N5', 'D9', 'M4']);
    expect(within(rowsOf('Liabilities')[0]).getByText('High').className).toBe('risk risk-high');
    await user.click(within(rowsOf('Liabilities')[3]).getByText('M4'));
    await user.click(screen.getByLabelText('In the CDRs only'));
    expect(rowsOf('Liabilities')).toHaveLength(3);
    await user.selectOptions(field('risk-filter'), 'HIGH');
    expect(rowsOf('Liabilities').map((r) => cells(r)[2])).toEqual(['Deamidation']);
  });

  it('fills in and analyzes an example', async () => {
    renderApp();

    await user.click(screen.getByRole('button', { name: 'Adalimumab' }));

    await screen.findByText(/Adalimumab \(Kabat CDRs\)/);
    expect(field('antibody-name').value).toBe('Adalimumab');
    expect(field<HTMLTextAreaElement>('antibody-heavy').value).toBe('QVQLQ');
  });

  it('loads the demonstration from its button', async () => {
    renderApp();

    await user.click(screen.getByRole('button', { name: 'Demonstration' }));

    await waitFor(() => expect(statusText()).toMatch(/^Demonstration loaded/));
    expect(activeTab()).toBe('Antibody');
  });

  it('reports the errors of the server in the status bar', async () => {
    api.analyze.mockRejectedValueOnce(new Error('boom'));
    renderApp();

    await user.click(screen.getByRole('button', { name: 'Analyze' }));

    await waitFor(() => expect(statusText()).toBe('Error: unexpected error (boom)'));
    expect(screen.getByRole('status').className).toBe('status-text error');
  });
});

describe('Peptide map tab', () => {
  it('shows the peptides, their modified forms, and copies their sequences', async () => {
    renderApp();
    await loadExample();
    await user.keyboard('{Alt>}2{/Alt}');
    expect(activeTab()).toBe('Peptide map');

    expect(screen.getByText('2 peptides, 1 with a liability, 1 covering a CDR')).toBeTruthy();
    const copy = screen.getByRole('button', { name: 'Copy the ProForma sequence' });
    expect(copy).toHaveProperty('disabled', true);
    expect(screen.getByText(/Select a peptide to see/)).toBeTruthy();

    const peptides = rowsOf('Peptide map');
    expect(cells(peptides[0])).toEqual([
      'Heavy',
      '1–22',
      'EVQLNGSGDYWGQGTLVTVSSK',
      '0',
      '2300.1234',
      'FR1, CDR1, FR2, Constant',
      'N5 Deamidation',
    ]);
    expect(peptides[0].className).toBe('has-liability');
    await user.click(within(peptides[0]).getByText('1–22'));

    const forms = rowsOf('Modified forms of the selected peptide');
    expect(forms.map(cells)).toEqual([
      [
        'N5',
        'Deamidation',
        'Deamidated',
        '2301.1074',
        '+0.9840',
        'EVQLN[Deamidated]GSGDYWGQGTLVTVSSK',
      ],
      [
        'D9',
        'Isomerization',
        'Succinimide',
        '2282.1128',
        '-18.0106',
        'EVQLNGSGD[Dehydrated]YWGQGTLVTVSSK',
      ],
    ]);

    await user.click(copy);
    expect(navigator.clipboard.writeText).toHaveBeenLastCalledWith(PEPTIDES[0].proForma);
    expect(statusText()).toBe(`Sequence copied: ${PEPTIDES[0].proForma}`);

    await user.click(within(forms[1]).getByText('D9'));
    await user.click(copy);
    expect(navigator.clipboard.writeText).toHaveBeenLastCalledWith(PEPTIDES[0].forms[1].proForma);

    await user.dblClick(within(forms[0]).getByText('N5'));
    expect(navigator.clipboard.writeText).toHaveBeenLastCalledWith(PEPTIDES[0].forms[0].proForma);
    await user.dblClick(within(rowsOf('Peptide map')[1]).getByText('AS'));
    expect(navigator.clipboard.writeText).toHaveBeenLastCalledWith('AS');

    await user.click(screen.getByLabelText('Peptides with a liability only'));
    expect(rowsOf('Peptide map')).toHaveLength(1);
  });

  it('digests again with the settings chosen', async () => {
    renderApp();
    await loadExample();
    await user.click(screen.getByRole('tab', { name: 'Peptide map' }));

    await user.selectOptions(field('protease'), 'LYS_C');
    fireEvent.change(field('missed-cleavages'), { target: { value: '2' } });
    fireEvent.change(field('min-length'), { target: { value: '5' } });
    fireEvent.blur(field('min-length'), { target: { value: '5' } });
    fireEvent.change(field('max-length'), { target: { value: '30' } });
    fireEvent.keyDown(field('max-length'), { key: 'Enter' });
    await user.click(screen.getByLabelText('Alkylated cysteines'));
    await user.click(screen.getByRole('button', { name: 'Digest' }));

    await waitFor(() =>
      expect(api.peptideMap).toHaveBeenLastCalledWith(expect.anything(), {
        protease: 'LYS_C',
        missedCleavages: 2,
        minLength: 5,
        maxLength: 30,
        alkylatedCysteines: false,
      }),
    );
    await waitFor(() => expect(statusText()).toBe('Peptide map: 2 peptides.'));
  });

  it('refuses a minimum length above the maximum', async () => {
    renderApp();
    await user.click(screen.getByRole('tab', { name: 'Peptide map' }));
    expect(screen.getByText(/Analyze an antibody in the “Antibody” tab/)).toBeTruthy();

    fireEvent.change(field('min-length'), { target: { value: '70' } });
    fireEvent.blur(field('min-length'), { target: { value: '70' } });
    await user.click(screen.getByRole('button', { name: 'Digest' }));

    expect(statusText()).toBe('Error: the minimum peptide length exceeds the maximum length.');
    expect(api.peptideMap).not.toHaveBeenCalled();
    expect(DEFAULT_DIGESTION.minLength).toBe(1);
  });
});

describe('LC-MS quantification tab', () => {
  it('compares the lots of the demonstration', async () => {
    renderApp();

    await user.keyboard('{Control>}d{/Control}');

    expect(activeTab()).toBe('LC-MS quantification');
    expect(
      await screen.findByText(
        'Demonstration data (Generic table (CSV)): 2 peptides in 3 samples ' +
          '(Lot A, Lot B, Stressed), no reading issue.',
      ),
    ).toBeTruthy();
    const table = screen.getByRole('table', { name: 'Lot comparison' });
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((h) => h.textContent),
    ).toEqual([
      'Chain',
      'Site',
      'Type',
      'Form',
      'Lot A (reference)',
      'Lot B',
      'Stressed',
      'Max. deviation',
      'Status',
    ]);
    const rows = rowsOf('Lot comparison');
    expect(cells(rows[0])).toEqual([
      'Heavy',
      'N5',
      'Deamidation',
      'Deamidated',
      '0.0%',
      '0.5%',
      '9.1%',
      '9.1 pts',
      'To review',
    ]);
    expect(rows[0].className).toBe('flagged');
    expect(cells(rows[1])[5]).toBe('–');
    expect(cells(rows[1])[8]).toBe('Comparable');
    await user.click(within(rows[1]).getByText('D9'));
    expect(
      screen.getByText('1 of 2 forms differ from the reference by more than the threshold.'),
    ).toBeTruthy();

    await user.selectOptions(field('reference'), 'Lot B');
    await waitFor(() =>
      expect(api.quantify).toHaveBeenLastCalledWith(expect.anything(), DEMO.peptides, 'Lot B', 2),
    );
    expect(screen.getByRole('columnheader', { name: 'Lot B (reference)' })).toBeTruthy();

    fireEvent.change(field('threshold'), { target: { value: '5' } });
    fireEvent.blur(field('threshold'), { target: { value: '5' } });
    await waitFor(() =>
      expect(api.quantify).toHaveBeenLastCalledWith(expect.anything(), DEMO.peptides, 'Lot B', 5),
    );
    await waitFor(() => expect(statusText()).toBe('Comparison: 1 of 2 forms to review.'));
  });

  it('shows the modification levels and the coverage', async () => {
    renderApp();
    await user.keyboard('{Control>}d{/Control}');
    await screen.findByText(/^Demonstration data/);

    await user.click(screen.getByRole('tab', { name: 'Modification levels' }));
    const site = rowsOf('Modification levels')[0];
    expect(cells(site)).toEqual([
      'Heavy',
      'N5',
      'Deamidation',
      'CDR1',
      'Stressed',
      '9.1%',
      'Deamidated 9.1%',
    ]);
    await user.click(within(site).getByText('Stressed'));

    await user.click(screen.getByRole('tab', { name: 'Sequence coverage' }));
    const coverage = rowsOf('Sequence coverage');
    expect(cells(coverage[0])).toEqual(['Heavy', 'Lot A', '22 / 24', '91.7%']);
    await user.click(within(coverage[1]).getByText('0 / 23'));
    expect(
      screen.getByText('2 observed sequence(s) found in no chain: PEPTIDE, SAMPLER'),
    ).toBeTruthy();
  });

  it('imports a result file in the format chosen, then removes it', async () => {
    renderApp();
    await user.keyboard('{Alt>}3{/Alt}');
    expect(screen.getByText(/^No LC-MS results/)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Remove the results' })).toHaveProperty(
      'disabled',
      true,
    );

    await user.selectOptions(field('ms-format'), 'MAXQUANT');
    const file = new File(['Sequence\tExperiment\tIntensity'], 'evidence.txt');
    await user.upload(screen.getByLabelText('Import results…'), file);

    expect(api.importResults).toHaveBeenCalledWith(file, 'MAXQUANT');
    expect(
      await screen.findByText(
        'evidence.txt (MaxQuant (evidence.txt)): 1 peptides in 2 samples (Lot B, Lot C), ' +
          '2 reading issues. Analyze the antibody to quantify its sites.',
      ),
    ).toBeTruthy();
    expect(screen.getByText('Reading issues (2)')).toBeTruthy();
    expect(screen.getByText('ERROR – line 4: missing intensity').className).toBe('error');
    expect(screen.getByText(/appears once the antibody is analyzed/)).toBeTruthy();
    expect([...field<HTMLSelectElement>('reference').options].map((o) => o.value)).toEqual(
      IMPORTED.samples,
    );

    await user.click(screen.getByRole('button', { name: 'Remove the results' }));
    expect(screen.getByText(/^No LC-MS results/)).toBeTruthy();
  });

  it('recognizes the format of a file and quantifies it with the antibody', async () => {
    api.importResults.mockResolvedValueOnce({ ...IMPORTED, issues: [] });
    renderApp();
    await loadExample();
    await user.click(screen.getByRole('tab', { name: 'LC-MS quantification' }));

    const input = screen.getByLabelText('Import results…');
    fireEvent.change(input, { target: { files: [] } });
    expect(api.importResults).not.toHaveBeenCalled();
    await user.upload(input, new File(['x'], 'psm.tsv'));

    expect(api.importResults).toHaveBeenCalledWith(expect.any(File), null);
    expect(await screen.findByText(/^psm.tsv \(MaxQuant .* no reading issue\.$/)).toBeTruthy();
    expect(api.quantify).toHaveBeenCalled();
  });

  it('opens the file chooser from the button, the menu and Ctrl+O', async () => {
    const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => undefined);
    renderApp();

    await user.keyboard('{Control>}o{/Control}');
    expect(activeTab()).toBe('LC-MS quantification');
    await user.click(screen.getByRole('button', { name: 'Import results…' }));
    await chooseMenu('File', 'Import LC-MS results…');

    expect(click).toHaveBeenCalledTimes(3);
  });
});

describe('QC report tab', () => {
  it('asks for an analysis first', async () => {
    renderApp();

    await user.keyboard('{Control>}s{/Control}');

    expect(activeTab()).toBe('QC report');
    expect(screen.getByText('Analyze an antibody first.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Download the PDF report' })).toHaveProperty(
      'disabled',
      true,
    );
    expect(screen.getByLabelText('LC-MS results and lot comparison')).toHaveProperty(
      'disabled',
      true,
    );
    await waitFor(() => expect(statusText()).toBe('Error: analyze an antibody first.'));
    expect(api.report).not.toHaveBeenCalled();
  });

  it('downloads the report with the content chosen', async () => {
    renderApp();
    await chooseMenu('File', 'Load the demonstration');
    await screen.findByText(/^Demonstration data/);
    await user.keyboard('{Alt>}4{/Alt}');

    expect(screen.getByText('Report of Trastuzumab, saved in your downloads.')).toBeTruthy();
    expect(field<HTMLSelectElement>('report-language').value).toBe('en');
    expect(field<HTMLSelectElement>('report-paper').value).toBe('LETTER');
    await user.selectOptions(field('report-language'), 'fr');
    await user.selectOptions(field('report-paper'), 'A4');
    await user.type(field('report-analyst'), 'QA');
    await user.type(field('report-notes'), 'Lots 1 to 3');
    await user.click(screen.getByLabelText('Theoretical peptide map'));
    const quantification = screen.getByLabelText('LC-MS results and lot comparison');
    await user.click(quantification);
    await user.click(quantification);
    await user.click(screen.getByRole('button', { name: 'Download the PDF report' }));

    await waitFor(() => expect(statusText()).toBe('Report Trastuzumab-QC-report.pdf downloaded.'));
    expect(api.report).toHaveBeenCalledWith(
      expect.objectContaining({
        locale: 'fr-FR',
        analyst: 'QA',
        notes: 'Lots 1 to 3',
        digestion: null,
        reference: 'Lot A',
        peptides: DEMO.peptides,
      }),
    );
    expect(URL.createObjectURL).toHaveBeenCalled();
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled();
  });

  it('downloads the report from the menu', async () => {
    renderApp();
    await loadExample();

    await chooseMenu('File', 'Download the QC report');

    await waitFor(() => expect(api.report).toHaveBeenCalled());
  });
});

describe('menus and shortcuts', () => {
  it('starts a new analysis', async () => {
    renderApp();
    await loadExample();
    await user.click(screen.getByRole('tab', { name: 'QC report' }));

    await chooseMenu('File', 'New analysis');

    expect(activeTab()).toBe('Antibody');
    expect(field('antibody-name').value).toBe('');
    expect(statusText()).toMatch(/^Ready/);
  });

  it('loads an example from the menu', async () => {
    renderApp();

    await chooseMenu('File', 'Example antibodies', 'Adalimumab');

    await waitFor(() => expect(field('antibody-name').value).toBe('Adalimumab'));
  });

  it('switches the language and remembers it', async () => {
    renderApp();

    await chooseMenu('Options', 'Language', 'Français');

    expect(screen.getByRole('tab', { name: 'Anticorps' })).toBeTruthy();
    expect(localStorage.getItem(LOCALE_KEY)).toBe('fr');
    expect(document.documentElement.lang).toBe('fr');
  });

  it('starts in French in a French browser', () => {
    renderApp(['fr-CA']);

    expect(screen.getByRole('tab', { name: 'Anticorps' })).toBeTruthy();
  });

  it('changes the text size from the menu and the keyboard', async () => {
    renderApp();
    const size = () => document.documentElement.style.fontSize;

    await chooseMenu('Options', 'Text size', 'Large');
    expect(size()).toBe('16px');
    await chooseMenu('Options', 'Text size', 'Larger text');
    expect(size()).toBe('17px');
    await chooseMenu('Options', 'Text size', 'Smaller text');
    expect(size()).toBe('16px');
    await chooseMenu('Options', 'Text size', 'Default text size');
    expect(size()).toBe('13px');

    await user.keyboard('{Control>}={/Control}');
    expect(size()).toBe('14px');
    await user.keyboard('{Control>}-{/Control}{Control>}-{/Control}');
    expect(size()).toBe('12px');
    await user.keyboard('{Control>}0{/Control}');
    expect(size()).toBe('13px');
    await user.keyboard('{Control>}x{/Control}');
    expect(size()).toBe('13px');
  });

  it('opens the guide at the section of the current tab', async () => {
    renderApp();
    await user.click(screen.getByRole('tab', { name: 'Peptide map' }));

    await user.keyboard('{F1}');

    const guide = screen.getByRole('complementary', { name: 'User guide' });
    expect(within(guide).getByRole('heading', { level: 1 }).textContent).toBe('Peptide map tab');
    await user.click(within(guide).getByTitle('Close'));
    expect(screen.queryByRole('complementary')).toBeNull();

    await chooseMenu('Help', 'User guide');
    expect(screen.getByRole('complementary', { name: 'User guide' })).toBeTruthy();
    await user.click(screen.getByRole('tab', { name: 'QC report' }));
    await user.click(screen.getByRole('button', { name: '?' }));
    expect(screen.getByRole('heading', { level: 1 }).textContent).toBe('QC report tab');
  });

  it('shows the shortcuts and ignores them while a dialog is open', async () => {
    renderApp();

    await chooseMenu('Help', 'Keyboard shortcuts');
    const dialog = screen.getByRole('dialog', { name: 'Keyboard shortcuts' });
    expect(within(dialog).getByText('Ctrl+D')).toBeTruthy();
    await user.keyboard('{Control>}d{/Control}');
    expect(api.demoResults).not.toHaveBeenCalled();

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('shows the about box', async () => {
    renderApp();

    await chooseMenu('Help', 'About MabScope');

    const dialog = screen.getByRole('dialog', { name: 'About MabScope' });
    expect(within(dialog).getByText(/Developed by Maxime Ethier/)).toBeTruthy();
    await user.click(within(dialog).getByRole('button', { name: 'Close' }));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('opens the references in a new tab', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    renderApp();

    await chooseMenu('Help', 'IMGT');
    await chooseMenu('Help', 'CDR definitions');
    await chooseMenu('Help', 'ProForma notation');

    expect(open.mock.calls.map((call) => call[0])).toEqual([
      'https://www.imgt.org',
      'http://www.bioinf.org.uk/abs/info.html',
      'https://www.psidev.info/proforma',
    ]);
  });
});
