import { fileURLToPath } from 'node:url';

/**
 * Die Konfiguration von Next.js 15 (App Router).
 *
 * <strong>Der Vertrag wird nicht auf dem Client erzeugt:</strong> die
 * Oberflaeche spricht die API ueber denselben Ursprung an (`/api/v1/...`)
 * und Next leitet die Anfrage an den Dienst weiter. Damit braucht die
 * Oberflaeche keine CORS-Konfiguration und keine fest verdrahtete Adresse.
 *
 * <strong>Der eingefrorene Vertrag</strong> (`docs/api/openapi.yaml`) ist die
 * einzige Quelle der Pfade und Felder — diese Datei kennt keine Operationen.
 */

/** Die Adresse des Dienstes, an die die Anfragen weitergeleitet werden. */
const apiBaseUrl = process.env.ECHOBOX_API_BASE_URL ?? 'http://localhost:8080';

/**
 * Die Wurzel des Builds.
 *
 * Next sucht sonst die hoechstgelegene `package-lock.json` — im Entwickler-
 * rechner kann das ausserhalb des Projekts liegen. Der Build soll aber nur
 * dieses Projekt umfassen.
 */
const outputFileTracingRoot = fileURLToPath(new URL('.', import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  outputFileTracingRoot,
  // Der Build prueft die Regeln nicht selbst: `next lint` ist in Next 15.5
  // ueberholt. `npm run lint` laeuft als eigener Schritt der Pipeline.
  eslint: { ignoreDuringBuilds: true },
  async rewrites() {
    return [
      {
        source: '/api/v1/:path*',
        destination: `${apiBaseUrl}/api/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
