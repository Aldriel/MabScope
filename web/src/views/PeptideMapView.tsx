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

import { useState } from 'react';
import {
  DIGESTION_LIMITS,
  type PeptideMapController,
} from '../controllers/usePeptideMapController';
import { useMessages } from '../i18n';
import { mass, positions, signedMass, site } from '../i18n/texts';
import { PROTEASES, type ModifiedForm, type Peptide, type Protease } from '../model/types';
import { DataTable } from './common/DataTable';
import { HelpButton } from './common/HelpButton';
import { NumberField } from './common/NumberField';

/** Residue and position of the site of a modified form, such as "N55". */
const formSite = (peptide: Peptide, form: ModifiedForm) =>
  site({ motif: peptide.sequence.charAt(form.position - peptide.start), position: form.position });

interface Props {
  ctl: PeptideMapController;
  onHelp: () => void;
}

/** "Peptide map" tab: theoretical peptides and the masses of their modified forms. */
export function PeptideMapView({ ctl, onHelp }: Props) {
  const m = useMessages();
  const { settings, peptides, selected } = ctl;
  const [formRow, setFormRow] = useState<ModifiedForm | null>(null);
  const forms = selected?.forms ?? [];
  const currentForm = formRow !== null && forms.includes(formRow) ? formRow : null;

  return (
    <div className="tab-content">
      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          void ctl.digest();
        }}
      >
        <label htmlFor="protease">{m.map.protease}</label>
        <select
          id="protease"
          value={settings.protease}
          onChange={(e) => ctl.change({ protease: e.target.value as Protease })}
        >
          {PROTEASES.map((protease) => (
            <option key={protease} value={protease}>
              {m.protease[protease]}
            </option>
          ))}
        </select>
        <span title={m.tip.missedCleavages}>{m.map.missedCleavages}</span>
        <NumberField
          id="missed-cleavages"
          value={settings.missedCleavages}
          {...DIGESTION_LIMITS.missedCleavages}
          title={m.tip.missedCleavages}
          label={m.map.missedCleavages}
          onChange={(missedCleavages) => ctl.change({ missedCleavages })}
        />
        <span>{m.map.length}</span>
        <NumberField
          id="min-length"
          value={settings.minLength}
          {...DIGESTION_LIMITS.minLength}
          label={m.map.minLength}
          onChange={(minLength) => ctl.change({ minLength })}
        />
        <span>–</span>
        <NumberField
          id="max-length"
          value={settings.maxLength}
          {...DIGESTION_LIMITS.maxLength}
          label={m.map.maxLength}
          onChange={(maxLength) => ctl.change({ maxLength })}
        />
        <label className="check" title={m.tip.alkylated}>
          <input
            type="checkbox"
            checked={settings.alkylatedCysteines}
            onChange={(e) => ctl.change({ alkylatedCysteines: e.target.checked })}
          />
          {m.map.alkylated}
        </label>
        <button type="submit" className="primary">
          {m.map.digest}
        </button>
        <span className="grow" />
        <HelpButton onClick={onHelp} />
      </form>

      <div className="row">
        <label className="check">
          <input
            type="checkbox"
            checked={ctl.liabilitiesOnly}
            onChange={(e) => ctl.setLiabilitiesOnly(e.target.checked)}
          />
          {m.map.liabilitiesOnly}
        </label>
        <span className="summary">
          {peptides.length > 0 &&
            m.map.summary(
              peptides.length,
              peptides.filter((p) => p.liabilities.length > 0).length,
              peptides.filter((p) => p.coversCdr).length,
            )}
        </span>
      </div>

      <DataTable
        className="peptide-table fill"
        label={m.tab.peptideMap}
        rows={ctl.shown}
        rowKey={(p) => `${p.chain}-${p.start}-${p.end}`}
        selected={selected}
        onSelect={ctl.select}
        onActivate={(p) => ctl.copy(p.proForma)}
        rowClassName={(p) => (p.liabilities.length > 0 ? 'has-liability' : undefined)}
        placeholder={m.map.placeholder}
        columns={[
          { header: m.column.chain, cell: (p) => p.chain },
          { header: m.column.positions, cell: (p) => positions(p.start, p.end), className: 'num' },
          { header: m.column.peptide, cell: (p) => p.sequence, className: 'mono' },
          { header: m.column.missed, cell: (p) => p.missedCleavages, className: 'num' },
          { header: m.column.mass, cell: (p) => mass(p.monoisotopicMass), className: 'num' },
          { header: m.column.regions, cell: (p) => p.regions.map((r) => m.region[r]).join(', ') },
          {
            header: m.column.liabilities,
            cell: (p) => p.liabilities.map((l) => `${site(l)} ${m.type[l.type]}`).join(', '),
          },
        ]}
      />

      <div className="row">
        <strong>{m.map.forms}</strong>
        <span className="grow" />
        <button
          type="button"
          disabled={selected === null}
          onClick={() => ctl.copy(currentForm?.proForma ?? selected!.proForma)}
        >
          {m.map.copyProForma}
        </button>
      </div>
      <DataTable
        className="form-table"
        label={m.map.forms}
        rows={forms}
        rowKey={(f) => f.proForma}
        selected={currentForm}
        onSelect={setFormRow}
        onActivate={(f) => ctl.copy(f.proForma)}
        placeholder={m.map.formsPlaceholder}
        columns={[
          // The forms listed are those of the selected peptide.
          { header: m.column.site, cell: (f) => formSite(selected!, f) },
          { header: m.column.type, cell: (f) => m.type[f.type] },
          { header: m.column.modification, cell: (f) => f.modification },
          { header: m.column.mass, cell: (f) => mass(f.monoisotopicMass), className: 'num' },
          {
            header: m.column.delta,
            cell: (f) => signedMass(f.monoisotopicMass - selected!.monoisotopicMass),
            className: 'num',
          },
          { header: m.column.proForma, cell: (f) => f.proForma, className: 'mono' },
        ]}
      />
    </div>
  );
}
