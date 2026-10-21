import { fileURLToPath } from 'node:url';

import { defineConfig } from 'vitest/config';

/**
 * Die Konfiguration der Komponententests (Vitest).
 *
 * <strong>Die Tests liegen in `tests/unit/frontend`</strong> (Schritt 5), nicht
 * unter `src/` — die Oberflaeche traegt keinen Testcode. Die Testumgebung ist
 * `jsdom`, die Setup-Datei bringt `@testing-library/jest-dom`.
 *
 * <strong>Der JSX-Transform:</strong> die `tsconfig.json` traegt
 * `jsx: preserve` (das verlangt Next.js). Fuer die Tests wird die
 * automatische JSX-Runtime gesetzt, sonst bliebe der JSX-Code unuebersetzt.
 */

/** Der Wurzelverzeichnis des Projekts — die Tests liegen ausserhalb der Oberflaeche. */
const workspaceRoot = fileURLToPath(new URL('../../..', import.meta.url));

/** Das Verzeichnis der Oberflaeche. */
const frontendRoot = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig({
  // Die Wurzel bleibt das Verzeichnis der Oberflaeche — die Tests liegen
  // ausserhalb. So findet die Konfiguration ihre Dateien unabhaengig davon,
  // aus welchem Verzeichnis sie gestartet wird.
  root: frontendRoot,
  esbuild: {
    jsx: 'automatic',
    jsxImportSource: 'react',
  },
  resolve: {
    alias: {
      '@': `${frontendRoot}src`,
    },
  },
  server: {
    fs: {
      allow: [workspaceRoot],
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    include: ['../../../tests/unit/frontend/**/*.test.tsx', '../../../tests/unit/frontend/**/*.test.ts'],
    css: false,
    restoreMocks: true,
  },
});
