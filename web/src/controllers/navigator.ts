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

/** Tabs of the application, in display order. */
export const TAB_IDS = ['antibody', 'peptideMap', 'quantification', 'report'] as const;
export type TabId = (typeof TAB_IDS)[number];

/** Section of the user guide describing each tab, as in the guide of the desktop application. */
export const HELP_SECTIONS: Record<TabId, string> = {
  antibody: 'antibody',
  peptideMap: 'peptide-map',
  quantification: 'quantification',
  report: 'report',
};

/** Copies a text to the clipboard; ignored when the browser refuses. */
export function copyToClipboard(text: string): void {
  navigator.clipboard?.writeText(text).catch(() => undefined);
}

/** Has the browser save a file in the downloads of the user. */
export function saveFile(content: Blob, fileName: string): void {
  const url = URL.createObjectURL(content);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.append(link);
  link.click();
  link.remove();
  // The download has started; the address is released once the browser has read it.
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
