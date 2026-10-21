/**
 * Die Vitest-Konfiguration der Projektwurzel.
 *
 * <strong>Eine Quelle:</strong> die eigentliche Konfiguration liegt beim
 * Frontend (`src/main/frontend/vitest.config.ts`); diese Datei reicht sie nur
 * weiter. So findet `npx vitest run` die Komponententests auch dann, wenn der
 * Befehl aus der Projektwurzel gestartet wird.
 */
export { default } from './src/main/frontend/vitest.config';
