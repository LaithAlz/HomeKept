import type { CapacitorConfig } from '@capacitor/cli';

/**
 * HomeKept — public **customer** app (remote-URL mode).
 * Published on the App Store + Play. Faces full public review (Apple guideline 4.2), so it
 * must ship genuine native value — push notifications (visit reminders, "report ready",
 * "tech on the way") are the primary justification.
 */
const config: CapacitorConfig = {
  appId: 'ca.homekept.app',
  appName: 'HomeKept',
  webDir: 'www',
  server: {
    url: 'https://homekept.ca/app',
    // Include Stripe so checkout / billing-portal flows stay in-app.
    allowNavigation: ['homekept.ca', 'api.homekept.ca', 'checkout.stripe.com', 'billing.stripe.com'],
    cleartext: false,
  },
  ios: { contentInset: 'always' },
};

export default config;
