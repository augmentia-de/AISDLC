import { createServer } from 'node:http';

/**
 * Der Dienst-Doppelgaenger der Browser-Tests.
 *
 * <strong>Warum ein Doppelgaenger?</strong> Die Oberflaeche ist ein Server
 * Component (ADR-018 §8): sie spricht den Dienst **serverseitig** an. Ein
 * Browser-Test kann eine solche Anfrage nicht abfangen (`page.route` wirkt
 * nur auf Anfragen des Browsers). Der Doppelgaenger steht deshalb **an der
 * Dienstgrenze** — die Oberflaeche spricht ihn ueber HTTP an, genau wie im
 * Betrieb. Der Weg, den der Browser-Request nimmt (Server → HTTP → Dienst),
 * ist damit vollstaendig abgedeckt.
 *
 * <strong>Der Doppelgaenger bedient den Vertrag</strong>
 * (`docs/api/openapi.yaml`): dieselben Pfade, dieselben Felder, dieselben
 * Statuscodes, dieselben Rollenregeln (F-03, V-39, E-01). Der HTTP-Vertrag
 * selbst wird in den Integrationstests gegen die echte Implementierung
 * geprueft.
 *
 * <strong>Die Steuerung</strong> laeuft ueber `/__control`:
 *  - `POST /__control/zustand`  setzt Endpunkte, Zustaelle und tote Zustellungen
 *  - `POST /__control/zuruecksetzen` setzt den Ausgangszustand
 *  - `GET  /__control/aufrufe`  liefert die aufgezeichneten Anfragen
 */

const PORT = Number(process.env.ECHOBOX_MOCK_PORT ?? 4010);

/** Der Endpunkt der Standarddaten. */
const ENDPOINT = {
  endpoint_id: '8f1c2f0e-2f4a-4a0e-9d5b-6a1c2e3f4a5b',
  organisation_id: 'm-1',
  target_url: 'https://orders.example.invalid/hooks/echobox',
  status: 'active',
  event_types: ['order.changed'],
  secret_version: 1,
  secret_rotated_at: null,
  paused_at: null,
  created_at: '2026-10-21T09:10:00.000Z',
  updated_at: '2026-10-21T09:10:00.000Z',
};

/** Der Zustelllauf der Standarddaten. */
const DELIVERY = {
  delivery_run_id: '7d2b1c9a-5e6f-4a0b-8c7d-1e2f3a4b5c6d',
  event_id: '3c9a7d21-8e4b-4a1f-9c2d-5e6f7a8b9c0d',
  endpoint_id: ENDPOINT.endpoint_id,
  organisation_id: 'm-1',
  event_type: 'order.changed',
  status: 'failed',
  attempt_count: 2,
  max_attempts: 8,
  replay_of: null,
  last_attempt_at: '2026-10-21T09:16:24.000Z',
  next_attempt_at: '2026-10-21T09:18:24.190Z',
  last_response_status: 503,
  last_error_code: null,
  rejection_reason: null,
  created_at: '2026-10-21T09:15:22.481Z',
  updated_at: '2026-10-21T09:16:24.190Z',
};

/** Die aufgegebene Zustellung der Standarddaten. */
const DEAD_DELIVERY = {
  delivery_run_id: 'b4c3d2e1-7f60-4a5b-9c8d-2e3f4a5b6c7d',
  event_id: '5d8e9f0a-1b2c-4d3e-8f90-a1b2c3d4e5f6',
  organisation_id: 'm-1',
  endpoint_id: ENDPOINT.endpoint_id,
  endpoint_url: ENDPOINT.target_url,
  event_type: 'order.changed',
  status: 'abandoned',
  attempt_count: 8,
  last_response_status: 503,
  last_attempt_at: '2026-10-21T10:22:11.900Z',
  abandoned_at: '2026-10-21T10:22:11.900Z',
  created_at: '2026-10-21T09:40:05.120Z',
};

/**
 * Baut den Ausgangszustand des Doppelgaengers.
 *
 * @returns der Ausgangszustand
 */
function ausgangszustand() {
  return {
    endpunkte: [structuredClone(ENDPOINT)],
    zustellungen: [structuredClone(DELIVERY)],
    tote: [structuredClone(DEAD_DELIVERY)],
  };
}

let zustand = ausgangszustand();
const aufrufe = [];

/**
 * Liest die Rollen aus dem Bearer-Token.
 *
 * @param req die Anfrage
 * @returns die Rollen und der Mandant der Identitaet
 */
function identitaet(req) {
  const header = req.headers.authorization ?? '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  const segment = token.split('.')[1] ?? '';

  try {
    const normalisiert = segment.replace(/-/g, '+').replace(/_/g, '/');
    const gefuellt = normalisiert.padEnd(normalisiert.length + ((4 - (normalisiert.length % 4)) % 4), '=');
    const rohdaten = Buffer.from(gefuellt, 'base64').toString('utf8');
    const nutzlast = JSON.parse(rohdaten);
    return {
      rollen: Array.isArray(nutzlast.roles) ? nutzlast.roles : [],
      organisationId: typeof nutzlast.organisation_id === 'string' ? nutzlast.organisation_id : null,
    };
  } catch {
    return { rollen: [], organisationId: null };
  }
}

/**
 * Sendet eine Antwort.
 *
 * @param res die Antwort
 * @param status der Statuscode
 * @param body der Koerper
 */
function sende(res, status, body) {
  const daten = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(daten),
  });
  res.end(daten);
}

/**
 * Sendet das Fehlerbild des Vertrags.
 *
 * @param res die Antwort
 * @param status der Statuscode
 * @param code der Fehlercode
 * @param detail die Meldung
 */
function fehler(res, status, code, detail) {
  sende(res, status, {
    type: `https://echobox.example/errors/${code}`,
    title: code,
    status,
    code,
    detail,
    violations: null,
  });
}

/**
 * Liest den Request-Body.
 *
 * @param req die Anfrage
 * @returns der Body als Text
 */
async function body(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString('utf8');
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', `http://127.0.0.1:${PORT}`);
  const pfad = url.pathname;
  const methode = req.method ?? 'GET';

  aufrufe.push({ methode, pfad, query: Object.fromEntries(url.searchParams) });

  // ---------------------------------------------------------------- Steuerung
  if (pfad === '/__control/zuruecksetzen' && methode === 'POST') {
    zustand = ausgangszustand();
    aufrufe.length = 0;
    sende(res, 200, { status: 'zurueckgesetzt' });
    return;
  }

  if (pfad === '/__control/zustand' && methode === 'POST') {
    const roh = await body(req);
    const neu = JSON.parse(roh);
    if (Array.isArray(neu.endpunkte)) {
      zustand.endpunkte = neu.endpunkte;
    }
    if (Array.isArray(neu.zustellungen)) {
      zustand.zustellungen = neu.zustellungen;
    }
    if (Array.isArray(neu.tote)) {
      zustand.tote = neu.tote;
    }
    sende(res, 200, { status: 'gesetzt' });
    return;
  }

  if (pfad === '/__control/aufrufe' && methode === 'GET') {
    sende(res, 200, { aufrufe });
    return;
  }

  // --------------------------------------------------------------- Gesundheit
  if (pfad === '/health' && methode === 'GET') {
    sende(res, 200, {
      status: 'up',
      service: 'echobox',
      version: '1.0.0',
      checked_at: new Date().toISOString(),
    });
    return;
  }

  if (pfad === '/health/ready' && methode === 'GET') {
    sende(res, 200, {
      status: 'ready',
      checks: { database: 'ok', delivery_worker: 'ok', tenant_context: 'ok' },
      checked_at: new Date().toISOString(),
    });
    return;
  }

  // ------------------------------------------------------------------ Endpunkte
  if (pfad === '/api/v1/endpoints' && methode === 'GET') {
    const { rollen } = identitaet(req);
    const sichtbar = rollen.includes('service_admin')
      ? zustand.endpunkte
      : zustand.endpunkte;
    sende(res, 200, {
      items: sichtbar,
      page: { limit: 50, has_more: false, next_cursor: null },
    });
    return;
  }

  if (pfad === '/api/v1/endpoints' && methode === 'POST') {
    const roh = await body(req);
    const anfrage = JSON.parse(roh);
    const neu = {
      endpoint_id: '1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d',
      organisation_id: 'm-1',
      target_url: anfrage.target_url,
      status: 'active',
      event_types: anfrage.event_types ?? [],
      secret_version: 1,
      secret_rotated_at: null,
      paused_at: null,
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    };
    zustand.endpunkte.push(neu);
    sende(res, 201, neu);
    return;
  }

  const endpunktAktion = /^\/api\/v1\/endpoints\/([^/]+)\/(pause|resume|secret-rotation)$/.exec(pfad);
  if (endpunktAktion !== null && methode === 'POST') {
    const endpunktId = endpunktAktion[1];
    const aktion = endpunktAktion[2];
    const endpunkt = zustand.endpunkte.find((eintrag) => eintrag.endpoint_id === endpunktId);

    if (endpunkt === undefined) {
      fehler(res, 404, 'not_found', 'Der Endpunkt wurde nicht gefunden.');
      return;
    }

    if (aktion === 'pause') {
      endpunkt.status = 'paused';
      endpunkt.paused_at = new Date().toISOString();
      sende(res, 200, endpunkt);
      return;
    }

    if (aktion === 'resume') {
      endpunkt.status = 'active';
      endpunkt.paused_at = null;
      sende(res, 200, endpunkt);
      return;
    }

    endpunkt.secret_version += 1;
    endpunkt.secret_rotated_at = new Date().toISOString();
    sende(res, 200, {
      endpoint_id: endpunkt.endpoint_id,
      secret_version: endpunkt.secret_version,
      rotated_at: endpunkt.secret_rotated_at,
      rotated_by: 'user-42@orders.example.invalid',
    });
    return;
  }

  // -------------------------------------------------------------- Zustellungen
  if (pfad === '/api/v1/deliveries' && methode === 'GET') {
    const status = url.searchParams.get('status');
    const endpointId = url.searchParams.get('endpoint_id');
    const von = url.searchParams.get('from');
    const bis = url.searchParams.get('to');

    let treffer = zustand.zustellungen;
    if (status !== null && status.length > 0) {
      treffer = treffer.filter((lauf) => status.split(',').includes(lauf.status));
    }
    if (endpointId !== null && endpointId.length > 0) {
      treffer = treffer.filter((lauf) => lauf.endpoint_id === endpointId);
    }
    if (von !== null && von.length > 0) {
      treffer = treffer.filter((lauf) => lauf.created_at >= von);
    }
    if (bis !== null && bis.length > 0) {
      treffer = treffer.filter((lauf) => lauf.created_at < bis);
    }

    sende(res, 200, {
      items: treffer,
      page: { limit: 50, has_more: false, next_cursor: null },
    });
    return;
  }

  const replay = /^\/api\/v1\/deliveries\/([^/]+)\/replay$/.exec(pfad);
  if (replay !== null && methode === 'POST') {
    const { rollen, organisationId } = identitaet(req);
    const laufId = replay[1];
    const lauf = zustand.zustellungen.find((eintrag) => eintrag.delivery_run_id === laufId);

    if (lauf === undefined) {
      fehler(res, 404, 'not_found', 'Der Zustelllauf wurde nicht gefunden.');
      return;
    }
    if (!rollen.includes('receiver_admin')) {
      fehler(res, 403, 'forbidden', 'Nur der Empfaenger-Admin darf das.');
      return;
    }
    if (organisationId !== null && organisationId !== lauf.organisation_id) {
      fehler(res, 404, 'not_found', 'Der Zustelllauf wurde nicht gefunden.');
      return;
    }

    sende(res, 202, {
      previous_delivery_run_id: laufId,
      delivery_run_id: 'c1d2e3f4-a5b6-4c7d-8e9f-0a1b2c3d4e5f',
      event_id: lauf.event_id,
      status: 'pending',
      attempt_count: 0,
      next_attempt_at: new Date().toISOString(),
      replayed_at: new Date().toISOString(),
    });
    return;
  }

  // ----------------------------------------------------------------- Totenliste
  if (pfad === '/api/v1/dead-deliveries' && methode === 'GET') {
    const { rollen } = identitaet(req);
    if (!rollen.includes('service_admin')) {
      fehler(res, 403, 'forbidden', 'Nur der Service-Administration ist die Totenliste zugänglich.');
      return;
    }
    sende(res, 200, {
      items: zustand.tote,
      page: { limit: 50, has_more: false, next_cursor: null },
    });
    return;
  }

  fehler(res, 404, 'not_found', 'Die Operation gibt es nicht.');
});

server.listen(PORT, '127.0.0.1', () => {
  process.stdout.write(`Dienst-Doppelgaenger laeuft auf http://127.0.0.1:${PORT}\n`);
});
