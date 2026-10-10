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
 * Regenerates the screenshots of the web user guide, in French and English, by driving the real
 * application in Microsoft Edge. Images are written to src/help/images/<lang>. Start the server
 * module (port 7071) and "npm run dev" first, then run "npm run screenshots". Another address can
 * be given with the BASE_URL environment variable.
 */
import { mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:5174/';
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const IMAGES = resolve(ROOT, 'src/help/images');
/** Screenshots are taken at this scale and drawn smaller by the guide, so they stay sharp. */
const SCALE = 1.5;
const LOCALES = ['fr', 'en'];
/** Longest wait for a request to the server, in milliseconds. */
const TIMEOUT_MS = 30_000;

/** Visible tab. */
const TAB = '.tab-panel:not([hidden]) ';

/** Steps of the guide: the tab shown, then what the user does before the screenshot. */
const STEPS = [
    {
        file: '01-antibody.png',
        tab: 1,
        action: async (page) => {
            await page.click(`${TAB}form button:has-text("Trastuzumab")`);
            await settle(page);
            await page.locator(`${TAB}.liability-table tbody tr`).first().click();
        },
    },
    {
        file: '02-peptide-map.png',
        tab: 2,
        action: (page) =>
            page.locator(`${TAB}.peptide-table tbody tr.has-liability`).nth(1).click(),
    },
    {
        file: '03-comparison.png',
        tab: 3,
        action: async (page) => {
            await page.keyboard.press('Control+KeyD');
            await settle(page);
            await page.locator(`${TAB}.comparison-table tbody tr.flagged`).first().click();
        },
    },
    {
        file: '04-sites.png',
        tab: 3,
        action: (page) => page.locator(`${TAB}.tab-bar.inner .tab`).nth(1).click(),
    },
    {
        file: '05-report.png',
        tab: 4,
        action: async (page) => {
            await page.fill('#report-analyst', 'Maxime Ethier');
            await page.fill('#report-notes', 'Lots A and B, stressed sample (40 °C, 4 weeks).');
        },
    },
];

/** Waits until no request is running. */
async function settle(page) {
    await page.waitForTimeout(150);
    await page.waitForSelector('.status-bar .spinner.idle', {
        state: 'attached',
        timeout: TIMEOUT_MS,
    });
}

async function runLocale(browser, locale) {
    const output = resolve(IMAGES, locale);
    await mkdir(output, { recursive: true });
    const page = await browser.newPage({
        viewport: { width: 1280, height: 800 },
        deviceScaleFactor: SCALE,
        locale: `${locale}-CA`,
    });
    await page.addInitScript((language) => {
        localStorage.setItem('mabscope.locale', language);
        localStorage.setItem('mabscope.fontSize', '13');
    }, locale);
    await page.goto(BASE_URL);
    await page.waitForSelector('.tab');
    console.log(locale + ':');

    for (const step of STEPS) {
        await page.keyboard.press('Alt+Digit' + step.tab);
        try {
            await step.action(page);
            await settle(page);
        } catch (e) {
            console.error(`  ${step.file}: ${e.message.split('\n')[0]}`);
        }
        await page.mouse.move(0, 0);
        await page.waitForTimeout(500);
        await page.screenshot({ path: resolve(output, step.file) });
        console.log(`  ${step.file}  —  ${await page.locator('.status-text').textContent()}`);
    }
    await page.close();
}

const browser = await chromium.launch({ channel: 'msedge' });
try {
    for (const locale of LOCALES) {
        await runLocale(browser, locale);
    }
    console.log('Screenshots written to ' + IMAGES);
} finally {
    await browser.close();
}
