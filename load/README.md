# Load tests

PLAN.md §9: 150 virtual users, which is the concurrency the system was specified for.

## Running

Needs [k6](https://k6.io/docs/get-started/installation/) and a running backend:

```bash
brew install k6
```

```bash
RATE_LIMIT_ENABLED=false mvn -f ../backend/pom.xml spring-boot:run
```

```bash
k6 run load/seat-claim.js
```

**Run it with the throttle off.** `deskdibs.rate-limit` allows 30 booking operations per user per
minute, so a load test that leaves it on measures the token bucket rather than the system — every
virtual user would spend its budget in the first few seconds and then read 429s for the rest of the
run. The anti-double-booking invariant is a database constraint and is unaffected either way.

## What each scenario proves

| Scenario | Question it answers |
|---|---|
| `stampede` | 150 users claiming **one** seat: exactly one wins, the rest get clean 409s, and no 500s. The headline guarantee, under real HTTP rather than in-process threads. |
| `browse` | 150 users reading the seat map: does the read path hold its latency budget while the floor is busy. |

The stampede is deliberately the same shape as `ConcurrentSeatClaimTest`, which proves the invariant
against the database directly. This one adds the parts that test cannot see: the connection pool,
Tomcat's thread pool, JSON serialisation, and the filter chain.
