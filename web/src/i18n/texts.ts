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
 * Texts displayed in the tables, shared by the views: the counterpart of the Texts class of the
 * desktop application, so that both interfaces write the same values the same way.
 */
import type { Liability } from '../model/types';

/** Residue and position of a site, such as "N55". */
export const site = (liability: Pick<Liability, 'motif' | 'position'>) =>
  liability.motif.charAt(0) + liability.position;

/** Range of positions, such as "31–35". */
export const positions = (start: number, end: number) => `${start}–${end}`;

/**
 * Mass with four decimals and a dot whatever the language, so that it can be copied into a
 * search engine.
 */
export const mass = (value: number) => value.toFixed(4);

/** Mass shift with its sign, such as "+0.9840" or "-17.0265". */
export const signedMass = (value: number) => (value >= 0 ? '+' : '') + mass(value);
