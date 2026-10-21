import { resolve } from 'node:path';

import { defineConfig, devices } from '@playwright/test';

/**
 * Die Konfiguration der Browser-Tests (Playwright).
 *
 * <strong>Die Tests liegen in `tests/e2e`** (Schritt 5), nicht unter
 * `src/`. `baseURL` kommt aus der Konfiguration; der Standard zeigt auf den
 * Entwicklungsserver der Oberflaeche.
 *
 * <strong>Zwei Webserver:</strong> die Oberflaeche spricht den Dienst
 * **serverseitig** an (ADR-018 §8 — Server Components). Der Browser-Test
 * braucht deshalb einen Dienst, mit dem die Oberflaeche sprechen kann:
 * standardmaessig ist das der **Dienst-Doppelgaenger**
 * (`tests/e2e/support/mock-api.mjs`), der den Vertrag bedient. Mit
 * `ECHOBOX_API_BASE_URL` zeigt die Konfiguration auf einen echten Dienst —
 * dann wird kein Doppelgaenger gestartet.
 */

/** Die Adresse der Oberflaeche. */
const baseURL = process.env.ECHOBOX_WEB_BASE_URL ?? 'http://127.0.0.1:3000';

/** Der Port der Oberflaeche. */
const port = process.env.ECHOBOX_WEB_PORT ?? '3000';

/** Der Port des Dienst-Doppelgaengers. */
const mockPort = process.env.ECHOBOX_MOCK_PORT ?? '4010';

/** Die Adresse des Dienst-Doppelgaengers. */
const dienstAdresse = process.env.ECHOBOX_API_BASE_URL ?? `http://127.0.0.1:${mockPort}`;

/** Das Wurzelverzeichnis des Projekts. */
const projektWurzel = resolve(__dirname, '../../..');

/** Der Dienst-Doppelgaenger — nur, wenn kein echter Dienst vorgegeben ist. */
const doppelgaenger = {
  command: 'node tests/e2e/support/mock-api.mjs',
  cwd: projektWurzel,
  url: `http://127.0.0.1:${mockPort}/health`,
  reuseExistingServer: process.env.CI === undefined,
  timeout: 30_000,
  env: { ECHOBOX_MOCK_PORT: mockPort },
};

/** Die Oberflaeche — sie spricht den Dienst ueber `ECHOBOX_API_BASE_URL` an. */
const oberflaeche = {
  command: process.env.ECHOBOX_WEB_COMMAND ?? `npm run start -- --port ${port}`,
  url: baseURL,
  reuseExistingServer: process.env.CI === undefined,
  timeout: 120_000,
  env: { ECHOBOX_API_BASE_URL: dienstAdresse },
};

export default defineConfig({
  testDir: '../../../tests/e2e',
  fullyParallel: false,
  forbidOnly: process.env.CI !== undefined,
  retries: process.env.CI !== undefined ? 1 : 0,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer:
    process.env.ECHOBOX_API_BASE_URL === undefined
      ? [doppelgaenger, oberflaeche]
      : [oberflaeche],
});
