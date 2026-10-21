#!/usr/bin/env node
/**
 * Der Abgleich der Oberflaechen-Typen mit dem eingefrorenen Vertrag.
 *
 * <strong>Eine Quelle:</strong> `docs/api/openapi.yaml`. Die Typen in
 * `src/types/api.ts` sind daraus abgeleitet; dieser Check stellt sicher, dass
 * die Ableitung aktuell ist. Ein Feld, das im Vertrag neu ist und in der
 * Oberflaeche fehlt (oder umgekehrt), bricht den Check — und damit die CI.
 *
 * <strong>Der Vertrag wird nicht geaendert.</strong> Ein Vertragsdefekt ist
 * ein Befund fuer Schritt 2, kein Anlass zum Aendern der Datei hier.
 *
 * Aufruf: `npm run contract:check`
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { parse } from 'yaml';

const here = dirname(fileURLToPath(import.meta.url));
const contractPath = resolve(here, '../../../../docs/api/openapi.yaml');
const typesPath = resolve(here, '../src/types/api.ts');

/** Die Schemas, die die Oberflaeche abbildet — Schema -> Typname. */
const SCHEMA_TO_TYPE = {
  Problem: 'Problem',
  ValidationViolation: 'ValidationViolation',
  Page: 'Page',
  Endpoint: 'Endpoint',
  EndpointList: 'EndpointList',
  SecretRotationResponse: 'SecretRotationResponse',
  DeliveryRun: 'DeliveryRun',
  DeliveryList: 'DeliveryList',
  DeadDelivery: 'DeadDelivery',
  DeadDeliveryList: 'DeadDeliveryList',
  ReplayResponse: 'ReplayResponse',
};

/** Die Aufzaehlungen, die die Oberflaeche abbildet — Schema -> Typname. */
const ENUM_TO_TYPE = {
  EndpointStatus: 'EndpointStatus',
  DeliveryStatus: 'DeliveryStatus',
  DeadDeliveryStatus: 'DeadDeliveryStatus',
};

/**
 * Liest die Felder eines TypeScript-Interfaces.
 *
 * @param {string} source der Quelltext
 * @param {string} typeName der Name des Interfaces
 * @returns {string[]} die Feldnamen
 */
function fieldsOf(source, typeName) {
  const pattern = new RegExp(`export interface ${typeName} \\{([\\s\\S]*?)\\n\\}`, 'm');
  const match = source.match(pattern);
  if (match === null) {
    throw new Error(`Der Typ ${typeName} wurde in ${typesPath} nicht gefunden.`);
  }
  return [...match[1].matchAll(/^\s{2}([A-Za-z_][A-Za-z0-9_]*)\??:/gm)].map((entry) => entry[1]);
}

/**
 * Liest die Werte eines TypeScript-Union-Typs.
 *
 * @param {string} source der Quelltext
 * @param {string} typeName der Name des Typs
 * @returns {string[]} die Werte
 */
function membersOf(source, typeName) {
  const pattern = new RegExp(`export type ${typeName} =([\\s\\S]*?);`, 'm');
  const match = source.match(pattern);
  if (match === null) {
    throw new Error(`Der Typ ${typeName} wurde in ${typesPath} nicht gefunden.`);
  }
  return [...match[1].matchAll(/'([^']+)'/g)].map((entry) => entry[1]);
}

/**
 * Loest die Properties eines Schemas auf (auch bei `allOf`).
 *
 * @param {object} schemas die Schemas des Vertrags
 * @param {string} name der Name des Schemas
 * @returns {string[]} die Namen der Properties
 */
function propertiesOf(schemas, name) {
  const schema = schemas[name];
  if (schema === undefined) {
    throw new Error(`Das Schema ${name} steht nicht im Vertrag.`);
  }

  if (Array.isArray(schema.allOf)) {
    const names = new Set();
    for (const part of schema.allOf) {
      const reference = part.$ref;
      if (typeof reference === 'string') {
        for (const property of propertiesOf(schemas, reference.split('/').pop())) {
          names.add(property);
        }
      }
      if (part.properties !== undefined) {
        for (const property of Object.keys(part.properties)) {
          names.add(property);
        }
      }
    }
    return [...names];
  }

  return Object.keys(schema.properties ?? {});
}

const contract = parse(readFileSync(contractPath, 'utf8'));
const schemas = contract.components.schemas;
const types = readFileSync(typesPath, 'utf8');

const problems = [];

/**
 * Vergleicht zwei Mengen und meldet die Unterschiede.
 *
 * @param {string} label die Bezeichnung des Vergleichs
 * @param {string[]} expected die erwarteten Werte
 * @param {string[]} actual die tatsaechlichen Werte
 */
function compare(label, expected, actual) {
  const missing = expected.filter((value) => !actual.includes(value));
  const extra = actual.filter((value) => !expected.includes(value));

  for (const value of missing) {
    problems.push(`${label}: das Feld "${value}" steht im Vertrag, aber nicht im Typ.`);
  }
  for (const value of extra) {
    problems.push(`${label}: das Feld "${value}" steht im Typ, aber nicht im Vertrag.`);
  }
}

for (const [schemaName, typeName] of Object.entries(SCHEMA_TO_TYPE)) {
  compare(
    `${schemaName} -> ${typeName}`,
    propertiesOf(schemas, schemaName),
    fieldsOf(types, typeName),
  );
}

for (const [schemaName, typeName] of Object.entries(ENUM_TO_TYPE)) {
  compare(
    `${schemaName} -> ${typeName}`,
    schemas[schemaName].enum,
    membersOf(types, typeName),
  );
}

if (problems.length > 0) {
  process.stderr.write(`Die Typen der Oberflaeche weichen vom Vertrag ab:\n`);
  for (const problem of problems) {
    process.stderr.write(`  - ${problem}\n`);
  }
  process.exit(1);
}

process.stdout.write(
  `Die Typen der Oberflaeche stimmen mit dem Vertrag ueberein ` +
    `(${Object.keys(SCHEMA_TO_TYPE).length} Schemas, ${Object.keys(ENUM_TO_TYPE).length} Aufzaehlungen).\n`,
);
