# Ops

Deployment and operations notes live here as they materialize (issue #1, #12, #44):

- Render — backend service + Postgres (daily backups: verify, don't assume)
- Cloudflare — frontend Workers deploy, DNS, www → apex redirect
- Stripe — test/live mode strictly separated
- Sentry — backend + frontend projects
- UptimeRobot — `/api/health` every 5 minutes
