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

import { useMemo, useState } from 'react';
import type { AntibodyController } from '../controllers/useAntibodyController';
import { useMessages } from '../i18n';
import { positions, site } from '../i18n/texts';
import { EXAMPLES } from '../model/AppModel';
import {
  RISKS,
  SCHEMES,
  type Analysis,
  type Liability,
  type NumberingScheme,
  type RegionSpan,
  type Risk,
} from '../model/types';
import { DataTable } from './common/DataTable';
import { HelpButton } from './common/HelpButton';
import { SequenceView } from './SequenceView';

/** A row of the region table. */
interface RegionRow {
  chain: string;
  span: RegionSpan;
}

const regionRows = (analysis: Analysis | null): RegionRow[] =>
  analysis?.chains.flatMap((chain) => chain.regions.map((span) => ({ chain: chain.name, span }))) ??
  [];

const count = (liabilities: Liability[], risk: Risk) =>
  liabilities.filter((l) => l.risk === risk).length;

interface Props {
  ctl: AntibodyController;
  onHelp: () => void;
}

/** "Antibody" tab: sequences, CDRs and liabilities. */
export function AntibodyView({ ctl, onHelp }: Props) {
  const m = useMessages();
  const { analysis, input } = ctl;
  const [regionRow, setRegionRow] = useState<RegionRow | null>(null);
  const [liabilityRow, setLiabilityRow] = useState<Liability | null>(null);
  // The same row objects across renders, so that the selected one is found again.
  const rows = useMemo(() => regionRows(analysis), [analysis]);
  const all = analysis?.liabilities ?? [];

  return (
    <div className="tab-content antibody-tab">
      <form
        className="row"
        onSubmit={(e) => {
          e.preventDefault();
          void ctl.analyze();
        }}
      >
        <label htmlFor="antibody-name">{m.antibody.name}</label>
        <input
          id="antibody-name"
          className="name-input"
          value={input.name}
          placeholder={m.antibody.namePrompt}
          autoComplete="off"
          onChange={(e) => ctl.setInput({ name: e.target.value })}
        />
        <label htmlFor="antibody-scheme">{m.antibody.scheme}</label>
        <select
          id="antibody-scheme"
          value={input.scheme}
          title={m.tip.scheme}
          onChange={(e) => ctl.setInput({ scheme: e.target.value as NumberingScheme })}
        >
          {SCHEMES.map((scheme) => (
            <option key={scheme} value={scheme}>
              {m.scheme[scheme]}
            </option>
          ))}
        </select>
        <button type="submit" className="primary">
          {m.antibody.analyze}
        </button>
        <span className="separator" />
        <span>{m.antibody.examples}</span>
        {EXAMPLES.map((example) => (
          <button
            key={example.id}
            type="button"
            title={m.tip.examples}
            onClick={() => void ctl.loadExample(example.id)}
          >
            {example.name}
          </button>
        ))}
        <button type="button" title={m.tip.demo} onClick={() => void ctl.loadDemo()}>
          {m.antibody.demo}
        </button>
        <span className="grow" />
        <HelpButton onClick={onHelp} />
      </form>

      <div className="row fill-none sequences-input">
        <label className="chain-input">
          <span>{m.antibody.heavy}</span>
          <textarea
            id="antibody-heavy"
            className="mono-input"
            value={input.heavy}
            placeholder={m.antibody.heavyPrompt}
            spellCheck={false}
            onChange={(e) => ctl.setInput({ heavy: e.target.value })}
          />
        </label>
        <label className="chain-input">
          <span>{m.antibody.light}</span>
          <textarea
            id="antibody-light"
            className="mono-input"
            value={input.light}
            placeholder={m.antibody.lightPrompt}
            spellCheck={false}
            onChange={(e) => ctl.setInput({ light: e.target.value })}
          />
        </label>
      </div>

      <div className="summary">
        {analysis &&
          m.antibody.summary(
            analysis.name,
            m.scheme[analysis.scheme],
            all.length,
            count(all, 'HIGH'),
            count(all, 'MEDIUM'),
            all.filter((l) => l.inCdr).length,
          )}
      </div>

      <div className="antibody-results">
        <section className="panel sequence-panel" aria-label={m.antibody.sequences}>
          {analysis ? (
            <SequenceView chains={analysis.chains} liabilities={analysis.liabilities} />
          ) : (
            <p className="placeholder">{m.antibody.placeholder}</p>
          )}
        </section>

        <DataTable
          className="region-table"
          label={m.antibody.regions}
          rows={rows}
          rowKey={(row) => `${row.chain}-${row.span.region}`}
          selected={regionRow !== null && rows.includes(regionRow) ? regionRow : null}
          onSelect={setRegionRow}
          rowClassName={(row) => (row.span.cdr ? 'cdr' : undefined)}
          placeholder={m.antibody.placeholder}
          columns={[
            { header: m.column.chain, cell: (row) => row.chain },
            { header: m.column.region, cell: (row) => m.region[row.span.region] },
            {
              header: m.column.positions,
              cell: (row) => positions(row.span.start, row.span.end),
              className: 'num',
            },
            {
              header: m.column.length,
              cell: (row) => row.span.end - row.span.start + 1,
              className: 'num',
            },
            {
              header: m.column.sequence,
              cell: (row) => row.span.sequence,
              className: 'mono wrap',
            },
          ]}
        />

        <div className="liability-pane">
          <div className="row">
            <strong>{m.antibody.liabilities}</strong>
            <label className="check" title={m.tip.cdrOnly}>
              <input
                type="checkbox"
                checked={ctl.cdrOnly}
                onChange={(e) => ctl.setCdrOnly(e.target.checked)}
              />
              {m.antibody.cdrOnly}
            </label>
            <select
              id="risk-filter"
              aria-label={m.tip.riskFilter}
              title={m.tip.riskFilter}
              value={ctl.minimumRisk}
              onChange={(e) => ctl.setMinimumRisk(e.target.value as Risk)}
            >
              {RISKS.map((risk) => (
                <option key={risk} value={risk}>
                  {m.riskFilter[risk]}
                </option>
              ))}
            </select>
          </div>
          <DataTable
            className="liability-table fill"
            label={m.antibody.liabilities}
            rows={ctl.liabilities}
            rowKey={(l) => `${l.chain}-${l.position}-${l.type}`}
            selected={
              liabilityRow !== null && ctl.liabilities.includes(liabilityRow) ? liabilityRow : null
            }
            onSelect={setLiabilityRow}
            placeholder={m.antibody.placeholder}
            columns={[
              { header: m.column.chain, cell: (l) => l.chain },
              { header: m.column.site, cell: (l) => site(l) },
              { header: m.column.type, cell: (l) => m.type[l.type] },
              { header: m.column.motif, cell: (l) => l.motif, className: 'mono' },
              { header: m.column.region, cell: (l) => m.region[l.region] },
              {
                header: m.column.risk,
                cell: (l) => (
                  <span className={`risk risk-${l.risk.toLowerCase()}`}>{m.risk[l.risk]}</span>
                ),
              },
            ]}
          />
        </div>
      </div>
    </div>
  );
}
