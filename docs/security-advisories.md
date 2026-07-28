# Accepted dependency advisories

`npm audit` findings we have assessed and are deliberately not acting on, with the
reasoning. Anything not listed here is unreviewed — treat a new finding as real
until it has been written up in this file.

Re-check this file whenever the affected dependency is upgraded: an advisory that
is unreachable today becomes reachable the moment the app starts using the
feature it applies to.

---

## GHSA-qwww-vcr4-c8h2 — react-router, CSRF bypass in RSC mode

- **Severity:** High
- **Affects:** `react-router` 7.12.0 – 8.2.0, via `react-router-dom`
- **Installed:** 7.18.1
- **Status:** accepted, not reachable

**Why we are not fixing it.** The advisory is specific to React Router's RSC
(React Server Components) mode, where an action can execute before the framework
returns a 400. DeskDibs is a Vite single-page app: it uses `BrowserRouter` with
plain client-side routes, and has no RSC, no server actions, no data `action`
handlers and no `@react-router/serve`. The vulnerable code path is not built into
the bundle and cannot be entered.

**Why not just upgrade.** There is no fixed release in the 7.x line — 7.18.1 is
the newest 7.x published, and the advisory range extends past it into 8.x. The
only remedy npm offers is `npm audit fix --force`, which *downgrades* to 7.11.0.
That is a breaking major-version move backwards, losing fixes we do rely on, to
silence a finding that cannot fire here. That trade is not worth taking.

**What would change this.** Revisit immediately if any of the following becomes
true:

- the app adopts RSC, server actions, or route `action` functions
- it moves to a React Router server runtime (`@react-router/serve`, framework mode)
- a fixed version is published in a range we can actually upgrade into

**Verifying the assessment yourself:**

```bash
grep -rn "createServerRouter\|@react-router/serve\|unstable_" frontend/src
```

An empty result means the RSC surface is still unused.
