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

import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { HELP_SECTIONS, TAB_IDS, type TabId } from './controllers/navigator';
import { useAntibodyController } from './controllers/useAntibodyController';
import { usePeptideMapController } from './controllers/usePeptideMapController';
import { useQuantificationController } from './controllers/useQuantificationController';
import { useReportController } from './controllers/useReportController';
import { useStore } from './controllers/useStore';
import { LOCALES, messagesOf, MessagesContext } from './i18n';
import { api as defaultApi, type Api } from './model/api';
import { AppModel, EXAMPLES } from './model/AppModel';
import { PRESET_FONT_SIZES, UiSettings, type SettingsStorage } from './model/settings';
import { AntibodyView } from './views/AntibodyView';
import { AboutDialog, ShortcutsDialog } from './views/Dialogs';
import { Footer } from './views/Footer';
import { HelpPanel } from './views/HelpPanel';
import { altShortcut, isShortcut, shortcut } from './views/keys';
import { MenuBar, type Menu } from './views/MenuBar';
import { PeptideMapView } from './views/PeptideMapView';
import { QuantificationView } from './views/QuantificationView';
import { ReportView } from './views/ReportView';
import { StatusBar } from './views/StatusBar';

const IMGT_SITE = 'https://www.imgt.org';
const ABYSIS_SITE = 'http://www.bioinf.org.uk/abs/info.html';
const PROFORMA_SITE = 'https://www.psidev.info/proforma';

const openUrl = (url: string) => window.open(url, '_blank', 'noopener,noreferrer');

interface Props {
  /** Operations of the server; replaced by a fake in the tests. */
  api?: Api;
  /** Where the settings are saved; localStorage by default. */
  storage?: SettingsStorage;
  /** Preferred languages of the browser. */
  languages?: readonly string[];
}

/**
 * Root of the web application, the counterpart of the main window of the desktop application:
 * menu bar, one tab per tool, status bar and footer, with the user guide docked on the right
 * when it is open. Inactive tabs are hidden rather than unmounted, so they keep their state.
 */
export default function App({
  api = defaultApi,
  storage = window.localStorage,
  languages = window.navigator.languages,
}: Props) {
  const [settings] = useState(() => new UiSettings(storage, languages));
  const [model] = useState(() => new AppModel(api));
  const locale = useStore(settings.locale);
  const fontSize = useStore(settings.fontSize);
  const m = messagesOf(locale);

  const [active, setActive] = useState<TabId>('antibody');
  const [help, setHelp] = useState<{ section: string; serial: number } | null>(null);
  const [dialog, setDialog] = useState<'about' | 'shortcuts' | null>(null);

  const showHelp = useCallback(
    (section: string) => setHelp((current) => ({ section, serial: (current?.serial ?? 0) + 1 })),
    [],
  );
  const closeHelp = useCallback(() => setHelp(null), []);

  const antibody = useAntibodyController(model);
  const peptideMap = usePeptideMapController(model);
  const quantification = useQuantificationController(model);
  const report = useReportController(model, locale, languages);

  // The text size is the font size of the page: every other size is in em or rem.
  useLayoutEffect(() => {
    document.documentElement.style.fontSize = `${fontSize}px`;
  }, [fontSize]);
  useEffect(() => {
    document.documentElement.lang = locale;
  }, [locale]);

  const newAnalysis = () => {
    setActive('antibody');
    model.clear();
  };
  const loadDemo = () => {
    setActive('quantification');
    void model.loadDemo();
  };
  const importResults = () => {
    setActive('quantification');
    quantification.chooseFile();
  };
  const downloadReport = () => {
    setActive('report');
    void report.download();
  };

  // Shortcuts of the window; Alt+1…4 replaces Ctrl+1…4, which browsers keep for their tabs.
  const keyActions = useRef<(e: KeyboardEvent) => void>(() => undefined);
  keyActions.current = (e: KeyboardEvent) => {
    if (document.querySelector('.modal')) {
      return;
    }
    const tabIndex = ['Digit1', 'Digit2', 'Digit3', 'Digit4'].indexOf(e.code);
    const key = e.key.toLowerCase();
    let action: (() => void) | null = null;
    if (e.key === 'F1') {
      action = () => showHelp(HELP_SECTIONS[active]);
    } else if (e.altKey && !e.ctrlKey && !e.metaKey && tabIndex >= 0) {
      action = () => setActive(TAB_IDS[tabIndex]);
    } else if (isShortcut(e)) {
      if (key === 'd') {
        action = loadDemo;
      } else if (key === 'o') {
        action = importResults;
      } else if (key === 's') {
        action = downloadReport;
      } else if (e.key === '=' || e.key === '+' || e.code === 'NumpadAdd') {
        action = () => settings.increaseFontSize();
      } else if (e.key === '-' || e.code === 'NumpadSubtract') {
        action = () => settings.decreaseFontSize();
      } else if (e.key === '0' || e.code === 'Numpad0') {
        action = () => settings.resetFontSize();
      }
    }
    if (action) {
      e.preventDefault();
      action();
    }
  };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => keyActions.current(e);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  const menus: Menu[] = [
    {
      label: m.menu.file,
      items: [
        { label: m.menu.newAnalysis, action: newAnalysis },
        { label: m.menu.demo, shortcut: shortcut('D'), action: loadDemo },
        {
          label: m.menu.examples,
          items: EXAMPLES.map((example) => ({
            label: example.name,
            action: () => {
              setActive('antibody');
              void model.loadExample(example.id);
            },
          })),
        },
        'separator',
        { label: m.menu.import, shortcut: shortcut('O'), action: importResults },
        { label: m.menu.report, shortcut: shortcut('S'), action: downloadReport },
      ],
    },
    {
      label: m.menu.options,
      items: [
        {
          label: m.menu.language,
          items: LOCALES.map((l) => ({
            label: messagesOf(l).languageName,
            checked: l === locale,
            action: () => settings.setLocale(l),
          })),
        },
        {
          label: m.menu.textSize,
          items: [
            ...PRESET_FONT_SIZES.map((size, index) => ({
              label: m.menu.textSizes[index],
              checked: size === fontSize,
              action: () => settings.setFontSize(size),
            })),
            'separator' as const,
            {
              label: m.menu.zoomIn,
              shortcut: shortcut('='),
              action: () => settings.increaseFontSize(),
            },
            {
              label: m.menu.zoomOut,
              shortcut: shortcut('−'),
              action: () => settings.decreaseFontSize(),
            },
            {
              label: m.menu.zoomReset,
              shortcut: shortcut('0'),
              action: () => settings.resetFontSize(),
            },
          ],
        },
      ],
    },
    {
      label: m.menu.help,
      items: [
        { label: m.menu.guide, shortcut: 'F1', action: () => showHelp(HELP_SECTIONS[active]) },
        { label: m.menu.shortcuts, action: () => setDialog('shortcuts') },
        'separator',
        { label: m.menu.imgt, action: () => openUrl(IMGT_SITE) },
        { label: m.menu.abysis, action: () => openUrl(ABYSIS_SITE) },
        { label: m.menu.proforma, action: () => openUrl(PROFORMA_SITE) },
        'separator',
        { label: m.menu.about, action: () => setDialog('about') },
      ],
    },
  ];

  const helpOf = (tab: TabId) => () => showHelp(HELP_SECTIONS[tab]);

  return (
    <MessagesContext.Provider value={m}>
      <div className="app">
        <MenuBar menus={menus} />
        <div className="app-body">
          <main className="app-main">
            <div className="tab-bar" role="tablist">
              {TAB_IDS.map((id, index) => (
                <button
                  key={id}
                  role="tab"
                  aria-selected={id === active}
                  className={id === active ? 'tab active' : 'tab'}
                  title={altShortcut(String(index + 1))}
                  onClick={() => setActive(id)}
                >
                  {m.tab[id]}
                </button>
              ))}
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'antibody'}>
              <AntibodyView ctl={antibody} onHelp={helpOf('antibody')} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'peptideMap'}>
              <PeptideMapView ctl={peptideMap} onHelp={helpOf('peptideMap')} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'quantification'}>
              <QuantificationView ctl={quantification} onHelp={helpOf('quantification')} />
            </div>
            <div className="tab-panel" role="tabpanel" hidden={active !== 'report'}>
              <ReportView ctl={report} onHelp={helpOf('report')} />
            </div>
          </main>
          {help && <HelpPanel locale={locale} request={help} onClose={closeHelp} />}
        </div>
        <StatusBar model={model} />
        <Footer language={locale} />
      </div>
      {dialog === 'about' && <AboutDialog onClose={() => setDialog(null)} />}
      {dialog === 'shortcuts' && <ShortcutsDialog onClose={() => setDialog(null)} />}
    </MessagesContext.Provider>
  );
}
