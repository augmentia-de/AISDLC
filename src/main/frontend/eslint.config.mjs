import { FlatCompat } from '@eslint/eslintrc';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

/**
 * Die ESLint-Konfiguration der Oberflaeche.
 *
 * <strong>Flat Config</strong> (ESLint 9) auf Basis von `eslint-config-next`
 * (Next.js 15). `next lint` ist in Next 15.5 ueberholt — der Build prueft die
 * Regeln deshalb nicht mehr selbst (siehe `next.config.mjs`), sondern
 * `npm run lint` laeuft als eigener Schritt.
 */

const directory = path.dirname(fileURLToPath(import.meta.url));
const compat = new FlatCompat({ baseDirectory: directory });

const config = [
  {
    ignores: ['.next/**', 'node_modules/**', 'next-env.d.ts', 'coverage/**'],
  },
  ...compat.extends('next/core-web-vitals', 'next/typescript'),
  {
    rules: {
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
    },
  },
];

export default config;
