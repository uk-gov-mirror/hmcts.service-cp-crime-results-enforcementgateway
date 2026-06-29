# Enforcement Hearing Gateway (service)

`service-cp-crime-results-enforcementgateway`

A Common Platform (CP) Spring Boot service that owns the **outbound integration from CP to the
Libra/GoB enforcement system for court hearings**.

When an enforcement case is allocated to a court hearing — or an existing allocation is amended — this
service keeps Libra (GoB) in sync with the confirmed hearing details:

- **Hearing confirmation:** on allocation, POST a `confirmedHearing` payload
  (`caseUrn`, `courtHearingLocation`, `dateOfHearing`, `timeOfHearing`) to Libra via APIM.
- **Hearing updates:** on any amendment to an allocated enforcement hearing, re-POST the latest
  `confirmedHearing` snapshot.

It is **event-driven**: it subscribes to CP listing public events (`public.listing.hearing-confirmed`
/ `public.listing.hearing-updated`), filters to enforcement cases, enriches, maps, and calls Libra.

> Owned by the **cp-case-ingestion-and-material** team. This service is the strangler-fig successor for
> the CP↔Libra/GoB enforcement integration currently in the legacy WildFly context
> `cpp-context-staging-enforcement`; that context is unchanged for now and will be migrated
> incrementally. Distinct from the GOB Resulting Workstream service, which owns results→GoB + NOWs.

API contract: [`api-cp-crime-results-enforcementgateway`](https://github.com/hmcts/api-cp-crime-results-enforcementgateway).

> ⚠️ **Scaffold.** Created from the HMCTS template
> [`service-hmcts-crime-springboot-template`](https://github.com/hmcts/service-hmcts-crime-springboot-template).
> The domain implementation (enforcement filter, enrichment, Libra client) is not yet built.
>
> ✅ **Spike done.** The prerequisite — proving a Spring Boot service can durably subscribe to the CP
> Artemis `public.event` topic and selector-filter listing events without the WildFly microservice
> framework — is proven on branch `team/artemis-public-event-poc`. See
> [`docs/artemis-public-event-poc.md`](docs/artemis-public-event-poc.md). Run it with
> `./gradlew test --tests "*ListingPublicEventConsumptionIT"`.

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
  artefact and triggers the Docker build/deploy via `ci-build-publish.yml` (with a Trivy image scan
  and a release-notes appender that records the published image coordinates).
- `code-analysis.yml`, `codeql.yml`, `secrets-scanner.yml`, `auto-merge-dependabot.yml`.

`main` and `team/*` branches are protected and require at least one approving review.

## Contributing

See [CONTRIBUTING.md](.github/CONTRIBUTING.md). Branch naming: `team/<topic>`.

## License

MIT — see [LICENSE](LICENSE).
