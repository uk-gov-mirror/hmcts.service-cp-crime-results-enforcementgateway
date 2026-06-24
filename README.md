# Enforcement Hearing Gateway (service)

`service-cp-crime-caseingestion-enforcementgateway`

A Common Platform (CP) Spring Boot service that owns the **CP → Libra/GoB outbound enforcement
hearing interfaces** for the **Enforcement 2025** programme (Jira **CCT-1222**).

When an enforcement case is allocated to a court hearing (or an existing allocation is amended), this
service confirms the hearing back to Libra (GoB):

- **FR09 — hearing confirmation:** on allocation, POST a `confirmedHearing` payload
  (`caseUrn`, `courtHearingLocation`, `dateOfHearing`, `timeOfHearing`) to Libra via APIM.
- **FR11 — hearing updates:** on any amendment to an allocated enforcement hearing, re-POST the
  latest `confirmedHearing` snapshot.

It is **event-driven**: it subscribes to CP listing public events (`public.listing.hearing-confirmed`
/ `public.listing.hearing-updated`), filters to enforcement cases, enriches, maps, and calls Libra.

> Owned by the **cp-case-ingestion-and-material** team. This service is the strangler-fig successor for
> the CP↔Libra/GoB enforcement integration currently in the legacy WildFly context
> `cpp-context-staging-enforcement`; that context is unchanged for now and will be migrated
> incrementally. Distinct from **GRW** (the GOB Resulting Workstream service), which owns
> results→GoB + NOWs.

Design: see the CCT-1222 *Listing and Hearing Confirmation Design* (Confluence `DATAIN/1985059634`).
API contract: [`api-cp-crime-caseingestion-enforcementgateway`](https://github.com/hmcts/api-cp-crime-caseingestion-enforcementgateway).

> ⚠️ **Scaffold.** Created from the HMCTS template
> [`service-hmcts-crime-springboot-template`](https://github.com/hmcts/service-hmcts-crime-springboot-template).
> The domain implementation (event listener, enforcement filter, Libra client) is not yet built — a
> platform spike to confirm Boot durable subscription to the CP Artemis `public.event` topic is a
> prerequisite (see the design doc §9).

## Tech stack

- **Java 25**, **Spring Boot 4**, **Gradle**
- Observability: Spring Boot Actuator, OpenTelemetry, Prometheus
- Hosting: Azure (App Insights, ACR/AKS via the ADO mirror pipeline)

## Prerequisites

- ☕️ **Java 25 or later** on your `PATH`
- ⚙️ **Gradle** (the wrapper pins the version — `gradle/wrapper/gradle-wrapper.properties`)

```bash
java -version
gradle -v
```

## Build & test

```bash
gradle build      # compile + checks + unit/integration tests
gradle test       # unit and integration tests only
```

### Static analysis (PMD)

```bash
gradle pmdTest
```

## CI/CD

GitHub Actions workflows live in `.github/workflows`:

- `ci-draft.yml` — build/verify on PRs and branch pushes.
- `ci-released.yml` — on a **published GitHub Release** (`release: [published]`), publishes the
  artefact and triggers the Docker build/deploy via `ci-build-publish.yml`.
- `code-analysis.yml`, `codeql.yml`, `secrets-scanner.yml`, `auto-merge-dependabot.yml`.

`main` and `team/*` branches are protected and require at least one approving review.

## Contributing

See [CONTRIBUTING.md](.github/CONTRIBUTING.md). Branch naming: `team/<topic>`.

## License

MIT — see [LICENSE](LICENSE).
