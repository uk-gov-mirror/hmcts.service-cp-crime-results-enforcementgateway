# POC / spike — consuming CP listing public events from Artemis

**Branch:** `team/artemis-public-event-poc`
**Status:** ✅ proven by an automated integration test against a real (embedded) Artemis broker.

## What this proves

The open question blocking the FR09/FR11 design (see the *Listing and Hearing Confirmation Design* page,
DATAIN/1985059634, §9) was:

> Can a plain **Spring Boot** service durably subscribe to the CP Artemis `public.event` topic and
> selector-filter for the listing events it cares about — *without* the WildFly microservice framework (MSF)?

This POC answers **yes**, and pins down exactly how:

| Concern | Finding |
| --- | --- |
| Topic | All public events flow through a single multicast topic — JMS destination `public.event` (`jms:topic:public.event`). |
| Wire format | MSF `JsonEnvelope`: a `_metadata` object (carrying the event `name`) plus the payload. |
| Event-name filter | The framework copies the event name onto the JMS string property **`CPPNAME`** (`DefaultEnvelopeConverter`). A broker-side message **selector** `CPPNAME IN ('public.listing.hearing-confirmed','public.listing.hearing-updated')` is all that's needed — non-matching events never reach the listener. |
| Durable subscription | A durable topic subscriber needs a stable `clientId`; it must own its connection (Spring Boot's shared/cached `ConnectionFactory` rejects `setClientID`), so the gateway uses a dedicated `ActiveMQConnectionFactory`. |
| Auth | Local dev broker has security disabled / `admin`-`admin`; CP env grants the `amq` role create-durable-queue + consume on `public.event`. No MSF identity required. |

## How it's wired (POC scope)

- `messaging/PublicEventJmsConfig` — a dedicated `ActiveMQConnectionFactory` (from `spring.artemis.*`) and a
  `DefaultJmsListenerContainerFactory` with `pubSubDomain=true`, `subscriptionDurable=true`, a stable `clientId`,
  and `autoStartup` gated by `enforcementgateway.messaging.listener-enabled` (default **off**, so the service and
  the actuator test start without a broker).
- `messaging/ListingPublicEventListener` — `@JmsListener` on `public.event` with the `CPPNAME` selector; parses
  the `JsonEnvelope` and projects it to `ConfirmedHearingEvent`.
- `messaging/ReceivedPublicEventSink` — POC seam recording consumed events for the test to assert on. In the real
  build this is replaced by the enforcement filter (by prosecuting authority) → enrichment → Libra `POST /confirmedHearing`.

Config (`application.yaml`):

```yaml
enforcementgateway:
  messaging:
    listener-enabled: ${ARTEMIS_LISTENER_ENABLED:false}
    public-event-topic: public.event
    subscription-name: service-cp-crime-results-enforcementgateway.public.event
    client-id: service-cp-crime-results-enforcementgateway
    selector: "CPPNAME IN ('public.listing.hearing-confirmed','public.listing.hearing-updated')"
```

## Run the proof (automated, no docker stack)

```bash
./gradlew test --tests "*ListingPublicEventConsumptionIT"
```

`ListingPublicEventConsumptionIT` boots a real embedded Artemis broker over TCP and asserts:

1. **Consume + parse** — a `public.listing.hearing-confirmed` envelope is received and its fields
   (`hearingId`, `sittingDay`, court location, case id, `isCivil`) are parsed correctly.
2. **Selector filtering** — a `public.listing.hearing-listed` event (not in the selector) is dropped by the
   broker, while a `public.listing.hearing-updated` event (in the selector) is delivered.

> The test first publishes probe events until one is consumed — `DefaultMessageListenerContainer` registers its
> subscriber asynchronously, and a message published to a topic before the subscription exists is silently
> dropped; the barrier makes the negative (selector) assertion meaningful.

## Run against the local dev environment (manual, optional)

1. Start the dev stack so Artemis (`activemq-ccm`) is up: `~/moj/cpp-dev-environment` (`./scripts/start.sh`).
   Broker `tcp://localhost:61616`; console http://localhost:8161 (admin/admin).
2. Start the service with the listener enabled:
   ```bash
   ARTEMIS_LISTENER_ENABLED=true ARTEMIS_BROKER_URL=tcp://localhost:61616 \
   ARTEMIS_USER=admin ARTEMIS_PASSWORD=admin ./gradlew bootRun
   ```
3. In the Artemis console → **Topics → public.event → Send Message**, send the body from
   `src/test/resources/events/public.listing.hearing-confirmed.sample.json` **with a string header
   `CPPNAME = public.listing.hearing-confirmed`**, then watch the service log the consumed event.

## Resolved vs still-open (for the build)

**Resolved:** topic name; envelope shape; the `CPPNAME` selector property + syntax; durable-subscriber wiring on
Spring Boot 4 (dedicated `ActiveMQConnectionFactory` + `clientId`); that no MSF identity is required.

**Still open (out of POC scope):**
- **Durable-sub `clientId` uniqueness across replicas** — two pods sharing one `clientId`/subscription name will
  clash; needs a per-instance strategy or shared-subscription semantics.
- **DLQ / redelivery / idempotency** — MSF gives WildFly contexts these for free; the Boot service must add a
  redelivery + idempotency/outbox mechanism.
- **Prod Artemis auth** — how the service acquires the `amq` role (credentials/secret source) in CP environments.
- **`originator` filtering** — some consumers (e.g. hearing-nows) filter on an `originator` payload field; confirm
  whether the gateway needs the same.
- **Schema-version awareness** — listing publishes the same public event name for v1 and v2 private events; confirm
  the gateway is tolerant of both payload shapes.
- **Future direction** — the listing team intend to expose listing events via a subscription model; if/when that
  lands, this durable topic subscriber is replaced by consuming the dedicated subscription.
