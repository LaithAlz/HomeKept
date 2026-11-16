import type { CapacitorConfig } from '@capacitor/cli';

/**
 * HomeKept **Tech** — internal technician app (remote-URL mode).
 * Distributed via TestFlight / Play internal testing (employees only) — no public review.
 */
const config: CapacitorConfig = {
  appId: 'ca.homekept.tech',
  appName: 'HomeKept Tech',
  webDir: 'www',
  server: {
    url: 'https://homekept.ca/tech',
    allowNavigation: ['homekept.ca', 'api.homekept.ca'],
    cleartext: false,
  },
  ios: { contentInset: 'always' },
};

export default config;
