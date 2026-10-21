import '@testing-library/jest-dom/vitest';

import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

/**
 * Die Setup-Datei der Komponententests.
 *
 * Sie bringt die Matcher von `@testing-library/jest-dom` und raeumt nach
 * jedem Testfall den DOM und den Browser-Speicher auf — sonst wuerden die
 * Rollen eines vorherigen Testfalls in den naechsten rutschen.
 */

afterEach(() => {
  cleanup();
  window.localStorage.clear();
});
