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
  RESULT_VIEWS,
  THRESHOLD_LIMITS,
  type FormatChoice,
  type QuantificationController,
} from '../controllers/useQuantificationController';
import { useMessages, type Messages } from '../i18n';
import { site } from '../i18n/texts';
import { DEMO_SOURCE } from '../model/AppModel';
import {
  MS_FORMATS,
  type Coverage,
  type MsImport,
  type SiteComparison,
  type SiteQuantity,
} from '../model/types';
import { DataTable, type Column } from './common/DataTable';
import { HelpButton } from './common/HelpButton';
import { NumberField } from './common/NumberField';

/** Most unmatched sequences listed below the coverage, as in the desktop application. */
export const MAX_UNMATCHED_SHOWN = 10;

/** Labels of the formats offered by the list, as the desktop application writes them. */
const FORMAT_CHOICES: readonly FormatChoice[] = ['AUTO', ...MS_FORMATS];

/** Summary of an import: source, format, peptides, samples and reading issues. */
function importSummary(m: Messages, imported: MsImport, source: string | null): string {
  return m.quant.summary(
    source === DEMO_SOURCE ? m.quant.demoSource : (source ?? ''),
    m.format[imported.format],
    imported.peptides.length,
    imported.samples.length,
    imported.samples.join(', '),
    imported.issues.length,
  );
}

/** Lists the forms of a site with their percentage, such as "Deamidated 2.1%". */
const formsOf = (m: Messages, s: SiteQuantity) =>
  Object.entries(s.percents)
    .map(([form, percent]) => `${form} ${m.percent(percent)}`)
    .join(', ');

/** Columns of the comparison table: the site, then one column per sample, the reference first. */
function comparisonColumns(
  m: Messages,
  samples: string[],
  reference: string,
): Column<SiteComparison>[] {
  const ordered = [reference, ...samples.filter((s) => s !== reference)];
  return [
    { header: m.column.chain, cell: (c) => c.site.chain },
    { header: m.column.site, cell: (c) => site(c.site) },
    { header: m.column.type, cell: (c) => m.type[c.site.type] },
    { header: m.column.form, cell: (c) => c.form },
    ...ordered.map((sample): Column<SiteComparison> => ({
      header: sample === reference ? m.quant.referenceColumn(sample) : sample,
      cell: (c) => (sample in c.percents ? m.percent(c.percents[sample]) : '–'),
      className: 'num',
    })),
    { header: m.column.deviation, cell: (c) => m.points(c.maxDeviation), className: 'num' },
    {
      header: m.column.status,
      cell: (c) => (c.flagged ? m.quant.flagged : m.quant.comparable),
    },
  ];
}

interface Props {
  ctl: QuantificationController;
  onHelp: () => void;
}

/** "LC-MS quantification" tab: import of the results, lot comparison, sites and coverage. */
export function QuantificationView({ ctl, onHelp }: Props) {
  const m = useMessages();
  const { imported, quantification } = ctl;
  const comparison = quantification?.comparison ?? null;
  const rows = comparison?.rows ?? [];
  const flagged = rows.filter((r) => r.flagged).length;
  const [comparisonRow, setComparisonRow] = useState<SiteComparison | null>(null);
  const [siteRow, setSiteRow] = useState<SiteQuantity | null>(null);
  const [coverageRow, setCoverageRow] = useState<Coverage | null>(null);
  const viewLabels = {
    comparison: m.quant.comparison,
    sites: m.quant.sites,
    coverage: m.quant.coverage,
  };
  const unmatched = quantification?.unmatchedSequences ?? [];

  return (
    <div className="tab-content">
      <div className="row">
        <button type="button" className="primary" title={m.tip.import} onClick={ctl.chooseFile}>
          {m.quant.import}
        </button>
        <input
          ref={ctl.fileInput}
          type="file"
          className="file-input"
          accept=".txt,.tsv,.csv,text/plain,text/csv,text/tab-separated-values"
          aria-label={m.quant.import}
          onChange={(e) => {
            const file = e.target.files?.[0];
            e.target.value = '';
            if (file) {
              void ctl.importFile(file);
            }
          }}
        />
        <label htmlFor="ms-format">{m.quant.format}</label>
        <select
          id="ms-format"
          value={ctl.format}
          title={m.tip.format}
          onChange={(e) => ctl.setFormat(e.target.value as FormatChoice)}
        >
          {FORMAT_CHOICES.map((format) => (
            <option key={format} value={format}>
              {m.format[format]}
            </option>
          ))}
        </select>
        <button type="button" title={m.tip.demo} onClick={() => void ctl.loadDemo()}>
          {m.antibody.demo}
        </button>
        <button type="button" disabled={imported === null} onClick={ctl.clear}>
          {m.quant.clear}
        </button>
        <span className="grow" />
        <HelpButton onClick={onHelp} />
      </div>

      <div className="summary import-summary">
        {imported === null
          ? m.quant.empty
          : importSummary(m, imported, ctl.source) +
            (ctl.hasAnalysis ? '' : ` ${m.quant.needsAnalysis}`)}
      </div>

      {imported !== null && imported.issues.length > 0 && (
        <details className="issues">
          <summary>
            {m.quant.issues} ({imported.issues.length})
          </summary>
          <ul className="issue-list">
            {imported.issues.map((issue, index) => (
              <li key={index} className={issue.severity === 'ERROR' ? 'error' : undefined}>
                {m.quant.issue(m.issue[issue.severity], issue.location, issue.message)}
              </li>
            ))}
          </ul>
        </details>
      )}

      <div className="tab-bar inner" role="tablist">
        {RESULT_VIEWS.map((view) => (
          <button
            key={view}
            type="button"
            role="tab"
            aria-selected={ctl.view === view}
            className={ctl.view === view ? 'tab active' : 'tab'}
            onClick={() => ctl.setView(view)}
          >
            {viewLabels[view]}
          </button>
        ))}
      </div>

      {ctl.view === 'comparison' && (
        <div className="result-view" role="tabpanel">
          <div className="row">
            <label htmlFor="reference">{m.quant.reference}</label>
            <select
              id="reference"
              value={ctl.reference}
              title={m.tip.reference}
              disabled={imported === null}
              onChange={(e) => void ctl.setReference(e.target.value)}
            >
              {(imported?.samples ?? []).map((sample) => (
                <option key={sample} value={sample}>
                  {sample}
                </option>
              ))}
            </select>
            <label htmlFor="threshold">{m.quant.threshold}</label>
            <NumberField
              id="threshold"
              value={ctl.threshold}
              min={THRESHOLD_LIMITS.min}
              max={THRESHOLD_LIMITS.max}
              step={THRESHOLD_LIMITS.step}
              title={m.tip.threshold}
              label={m.quant.threshold}
              onChange={(value) => void ctl.setThreshold(value)}
            />
            <span className="summary comparison-summary">
              {rows.length > 0 && m.quant.comparisonSummary(flagged, rows.length)}
            </span>
          </div>
          <DataTable
            className="comparison-table fill"
            label={m.quant.comparison}
            rows={rows}
            rowKey={(c) => `${c.site.chain}-${c.site.position}-${c.site.type}-${c.form}`}
            selected={comparisonRow !== null && rows.includes(comparisonRow) ? comparisonRow : null}
            onSelect={setComparisonRow}
            rowClassName={(c) => (c.flagged ? 'flagged' : undefined)}
            placeholder={imported === null ? m.quant.placeholder : m.quant.comparisonPlaceholder}
            columns={comparisonColumns(m, quantification?.samples ?? [], ctl.reference)}
          />
        </div>
      )}

      {ctl.view === 'sites' && (
        <div className="result-view" role="tabpanel">
          <DataTable
            className="site-table fill"
            label={m.quant.sites}
            rows={quantification?.sites ?? []}
            rowKey={(s) => `${s.site.chain}-${s.site.position}-${s.site.type}-${s.sample}`}
            selected={siteRow}
            onSelect={setSiteRow}
            placeholder={m.quant.placeholder}
            columns={[
              { header: m.column.chain, cell: (s) => s.site.chain },
              { header: m.column.site, cell: (s) => site(s.site) },
              { header: m.column.type, cell: (s) => m.type[s.site.type] },
              { header: m.column.region, cell: (s) => m.region[s.site.region] },
              { header: m.column.sample, cell: (s) => s.sample },
              {
                header: m.column.modified,
                cell: (s) => m.percent(s.modifiedPercent),
                className: 'num',
              },
              { header: m.column.forms, cell: (s) => formsOf(m, s) },
            ]}
          />
        </div>
      )}

      {ctl.view === 'coverage' && (
        <div className="result-view" role="tabpanel">
          <DataTable
            className="coverage-table fill"
            label={m.quant.coverage}
            rows={quantification?.coverage ?? []}
            rowKey={(c) => `${c.chain}-${c.sample}`}
            selected={coverageRow}
            onSelect={setCoverageRow}
            placeholder={m.quant.placeholder}
            columns={[
              { header: m.column.chain, cell: (c) => c.chain },
              { header: m.column.sample, cell: (c) => c.sample },
              {
                header: m.column.residues,
                cell: (c) => `${c.coveredResidues} / ${c.length}`,
                className: 'num',
              },
              { header: m.column.coverage, cell: (c) => m.percent(c.percent), className: 'num' },
            ]}
          />
          {unmatched.length > 0 && (
            <p className="muted unmatched">
              {m.quant.unmatched(
                unmatched.length,
                unmatched.slice(0, MAX_UNMATCHED_SHOWN).join(', '),
              )}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
