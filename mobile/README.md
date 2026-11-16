# HomeKept — native mobile apps (Capacitor)

**Two** native iOS + Android apps, both built with [Capacitor](https://capacitorjs.com) in
**remote-URL mode** (the native app loads the deployed web app and bridges native device
features into it):

| App | Dir | Bundle id | Loads | Distribution |
|---|---|---|---|---|
| **HomeKept** (customer) | `customer/` | `ca.homekept.app` | `homekept.ca/app` | **Public** App Store + Play (full review) |
| **HomeKept Tech** (technician) | `tech/` | `ca.homekept.tech` | `homekept.ca/tech` | **Internal** — TestFlight / Play internal (no public review) |

They share one web codebase; only the bundle id, icon, loaded URL, and distribution differ.

## Why remote-URL mode

The HomeKept frontend is **server-rendered** (TanStack Start on a Cloudflare Worker — the
build emits `client/` + `server/`, not a static SPA), so Capacitor can't bundle it
statically. In remote-URL mode (`server.url`) the shell loads the live site and Capacitor
injects its native bridge, so the web app can call native plugins when it detects it's
running inside the shell (`Capacitor.isNativePlatform()`). One codebase → web, PWA, native.

## The distribution split sets your launch date

- **Tech app → internal.** Employees only, so TestFlight (iOS) + Play internal testing —
  no public review. Ready as soon as it's built and signed.
- **Customer app → public review.** Listed publicly, so it faces the full Apple review,
  including guideline 4.2 ("minimum functionality" — a bare webview gets rejected). Push
  notifications for reminders/reports are what earn the pass. **This review — not the
  code — is the long pole in "launch with both ready."** Consider launching web + PWA +
  the internal tech app, then shipping the public customer app right after.

## Prerequisites (founder — these are blockers)

| Need | Why | Cost |
|---|---|---|
| **Frontend deployed to Cloudflare** | The shells point at `homekept.ca`; it must be live. | (in progress) |
| **Apple Developer account** | Build/sign/submit iOS. Needs this Mac + Xcode (present: 26.3). | $99/yr |
| **Google Play Developer account** | Publish Android. | $25 one-time |
| **CocoaPods** | Capacitor iOS dependency manager: `brew install cocoapods`. | free |
| **Android Studio + SDK** | Android builds. `ANDROID_HOME` is currently unset. | free |
| **App icon + splash (×2)** | 1024×1024 per app; use the master mark `frontend/public/icon.svg`. | — |
| **Store listing (customer)** | Name, description, screenshots, privacy nutrition labels, support URL. | — |

## Setup (per app — run inside `customer/` or `tech/`)

```bash
cd mobile/tech        # or: cd mobile/customer
npm install
npx cap add ios       # needs CocoaPods
npx cap add android   # needs Android SDK (ANDROID_HOME)
npx cap sync
npx cap open ios      # Xcode → run on device/simulator
npx cap open android  # Android Studio
```

`server.url` in each `capacitor.config.ts` points at production. For **local testing**
against a dev frontend, change it to your machine's LAN URL (e.g. `http://192.168.x.x:8080`)
and set `cleartext: true` temporarily.

## Native-feature roadmap (what makes each a real app, not a webview)

**Customer app** — push notifications first (visit reminders, "report ready", "tech on the
way"); this is the main 4.2 justification. Then biometric unlock. Billing stays in the
Stripe browser flow (a real-world service — no Apple/Google in-app-purchase cut required).

**Tech app** — native camera (visit photos → existing R2 signed-upload flow); push for new
assignments / day-sheet reminders (hooks into the backend reminder scheduler, V10 /
notification_log); biometric unlock.

Each is progressive: the web app feature-detects Capacitor and uses the native plugin when
present, falling back to web behaviour in a browser — so **one** codebase drives all three.

## Store submission checklist (per app)

- [ ] Bundle id registered (Apple + Google): `ca.homekept.app` / `ca.homekept.tech`.
- [ ] Signing: iOS provisioning profile / Android keystore (back the keystore up — losing it blocks all future updates).
- [ ] Icon + splash generated (`@capacitor/assets`).
- [ ] Privacy: data-collection disclosure (notifications, camera); privacy policy URL (ties to #121/#63).
- [ ] Customer app: screenshots per device class; App Store review notes explaining the native value.
- [ ] Tech app: TestFlight / Play internal track only.

## Status

Foundation only — `capacitor.config.ts`, `package.json`, offline fallback, and this plan
per app. Native projects (`ios/`, `android/`) are **not** generated yet (blocked on
CocoaPods + Android SDK). Nothing here submits to a store or touches an external account.
