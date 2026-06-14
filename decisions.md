# decisions.md

Registry of every external account and service HomeKept depends on, and the email used
to create each. **No secrets in this file** — passwords, API keys, and tokens live in the
password manager only. This is the index that tells you *which* vault entry to look for.

Tracks acceptance for [issue #1](../../issues/1) (Set up accounts and external services).

## Accounts & services

| Service | Purpose | Account email | Status | Notes |
|---|---|---|---|---|
| Domain (`.ca`) | Primary domain | _TBD_ | ☐ | Registrar TBD (Hover / Porkbun / Namecheap). Buy 2 years upfront. DNS → placeholder. |
| Stripe | Payments | _TBD_ | ☐ | Country: Canada. Business type Sole Proprietorship pre-incorporation → Corporation later. Test-mode keys → password manager. Verification: 1–3 business days. |
| Render | Backend + Postgres | _TBD_ | ☐ | Postgres provisioned; backend region = Postgres region (Oregon or Ohio — not Frankfurt). |
| Vercel | Frontend hosting | _TBD_ | ☐ | Linked to the (yet-empty) GitHub repo. |
| SendGrid | Transactional email | _TBD_ | ☐ | Prefer domain authentication over single sender; add DNS records to registrar (24h+ propagation). |
| Sentry | Error tracking | _TBD_ | ☐ | Two projects: backend + frontend. Free tier = 5K events/month. |
| UptimeRobot | Uptime monitoring | _TBD_ | ☐ | Free tier. |
| PostHog | Product analytics | _TBD_ | ☐ | Cloud US (arch doc §5.7). Free tier. Keys → env config. |
| Cloudflare R2 | Visit photo storage | _TBD_ | ☐ | Bucket TBD (arch doc §6.4). Free tier. Keys → env config. |

Mark a row ☑ once the account exists, its credentials are saved in the password manager,
and the email above is filled in.

## Where secrets live

- **Password manager** (1Password / Bitwarden / …): all account passwords, API keys, tokens.
- **Env config** (`hand-write`, founder-only): runtime keys for PostHog, R2, Stripe, etc.
  Never commit these; never paste them here.

## Slow-to-verify — start day 1

- **Stripe business verification** — 1–3 business days.
- **Domain DNS propagation** — hours.
- **SendGrid domain authentication** — 24h+ to propagate.

These gate week 3 (real Stripe payments). Kick them off before anything else.
