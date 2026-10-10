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

import type { ReportController } from '../controllers/useReportController';
import { LOCALES, useMessages } from '../i18n';
import type { Paper } from '../model/AppModel';
import { HelpButton } from './common/HelpButton';

const PAPERS: readonly Paper[] = ['LETTER', 'A4'];

interface Props {
  ctl: ReportController;
  onHelp: () => void;
}

/** "QC report" tab: content of the quality control report, downloaded as a PDF. */
export function ReportView({ ctl, onHelp }: Props) {
  const m = useMessages();
  const { options } = ctl;
  return (
    <div className="tab-content">
      <div className="row">
        <p className="report-intro grow">{m.report.intro}</p>
        <HelpButton onClick={onHelp} />
      </div>

      <div className="report-form">
        <label htmlFor="report-language">{m.report.language}</label>
        <select
          id="report-language"
          value={options.language}
          onChange={(e) => ctl.change({ language: e.target.value as 'en' | 'fr' })}
        >
          {LOCALES.map((language) => (
            <option key={language} value={language}>
              {m.reportLanguage[language]}
            </option>
          ))}
        </select>

        <label htmlFor="report-paper">{m.report.paper}</label>
        <select
          id="report-paper"
          value={options.paper}
          title={m.tip.paper}
          onChange={(e) => ctl.change({ paper: e.target.value as Paper })}
        >
          {PAPERS.map((paper) => (
            <option key={paper} value={paper}>
              {m.paper[paper]}
            </option>
          ))}
        </select>

        <label htmlFor="report-analyst">{m.report.analyst}</label>
        <input
          id="report-analyst"
          value={options.analyst}
          placeholder={m.report.analystPrompt}
          onChange={(e) => ctl.change({ analyst: e.target.value })}
        />

        <label htmlFor="report-notes">{m.report.notes}</label>
        <textarea
          id="report-notes"
          rows={4}
          value={options.notes}
          placeholder={m.report.notesPrompt}
          onChange={(e) => ctl.change({ notes: e.target.value })}
        />

        <span>{m.report.sections}</span>
        <div className="report-sections">
          <span className="muted">{m.report.always}</span>
          <label className="check">
            <input
              type="checkbox"
              checked={options.includePeptideMap}
              onChange={(e) => ctl.change({ includePeptideMap: e.target.checked })}
            />
            {m.report.includePeptideMap}
          </label>
          <label className="check">
            <input
              type="checkbox"
              disabled={!ctl.hasQuantification}
              checked={options.includeQuantification && ctl.hasQuantification}
              onChange={(e) => ctl.change({ includeQuantification: e.target.checked })}
            />
            {m.report.includeQuantification}
          </label>
        </div>
      </div>

      <div className="row">
        <button
          type="button"
          className="primary"
          disabled={ctl.analysisName === null}
          onClick={() => void ctl.download()}
        >
          {m.report.download}
        </button>
        <span className="muted report-readiness">
          {ctl.analysisName === null ? m.report.needsAnalysis : m.report.ready(ctl.analysisName)}
        </span>
      </div>
    </div>
  );
}
