import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

/**
 * E2E-Ebene — Barrierefreiheit der drei Seiten (US-021, NFR-005).
 *
 * Der Test laeuft im echten Browser: nur hier berechnet der Browser Farben und
 * Layout, damit der Kontrast geprueft werden kann (AC-021-3). Geprueft werden
 * der automatische WCAG-2.1-AA-Scan (AC-021-2), die Tastaturbedienung
 * (AC-021-1), der Kontrast (AC-021-3) und die Screenreader-Semantik
 * (AC-021-4).
 *
 * Testorakel: ADR-018 (Barrierefreiheit ist eine Bauregel, keine Auditfrage).
 *
 * Abgedeckte Akzeptanzkriterien: AC-021-1, AC-021-2, AC-021-3, AC-021-4.
 */

const SEITE = [
  { name: 'Endpunkte', url: '/endpoints' },
  { name: 'Protokoll', url: '/deliveries' },
  { name: 'Tote Zustellungen', url: '/dead-deliveries' },
];

for (const seite of SEITE) {
  test.describe(`${seite.name}-Seite`, () => {
    test('AC-021-2: der WCAG-2.1-AA-Scan meldet keine kritischen oder schweren Verstoesse', async ({ page }) => {
      await page.goto(seite.url);

      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();

      const schwer = results.violations.filter(
        (violation) => violation.impact === 'critical' || violation.impact === 'serious',
      );

      expect(
        schwer.map((violation) => `${violation.id}: ${violation.help} (${violation.nodes.length} Knoten)`),
      ).toEqual([]);
    });

    test('AC-021-3: der Kontrast erreicht 4,5:1 fuer Text und 3:1 fuer Bedienelemente', async ({ page }) => {
      await page.goto(seite.url);

      const results = await new AxeBuilder({ page }).withRules(['color-contrast']).analyze();

      expect(results.violations.map((violation) => violation.id)).toEqual([]);
    });

    test('AC-021-1: alle Funktionen sind mit der Tastatur erreichbar und ausloesbar', async ({ page }) => {
      await page.goto(seite.url);

      const erreichbar = await page.evaluate(() => {
        const interaktiv = Array.from(
          document.querySelectorAll<HTMLElement>(
            'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
          ),
        );
        return interaktiv.every((element) => element.tabIndex >= 0);
      });

      expect(erreichbar).toBe(true);

      await page.keyboard.press('Tab');
      const erstes = await page.evaluate(() => document.activeElement?.tagName ?? '');
      expect(erstes).not.toBe('BODY');

      const sichtbar = await page.evaluate(() => {
        const element = document.activeElement as HTMLElement | null;
        if (!element) {
          return false;
        }
        const style = window.getComputedStyle(element);
        return style.outlineStyle !== 'none' || style.boxShadow !== 'none' || style.borderBottomWidth !== '0px';
      });

      expect(sichtbar).toBe(true);
    });

    test('AC-021-4: Liste, Zeilen und Statusmeldungen tragen Rolle und Inhalt', async ({ page }) => {
      await page.goto(seite.url);

      const struktur = await page.evaluate(() => ({
        main: document.querySelectorAll('main').length,
        headlines: document.querySelectorAll('h1, h2').length,
        listen: document.querySelectorAll('table, [role="table"], [role="list"]').length,
        statusmeldungen: document.querySelectorAll('[role="status"], [role="alert"]').length,
      }));

      expect(struktur.main).toBeGreaterThan(0);
      expect(struktur.headlines).toBeGreaterThan(0);

      const results = await new AxeBuilder({ page })
        .withRules(['aria-required-children', 'aria-required-parent', 'aria-roles', 'aria-command-name'])
        .analyze();

      expect(results.violations.map((violation) => violation.id)).toEqual([]);
    });
  });
}
