package uk.gov.hmcts.cp.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.TextMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Durable subscriber to the CP listing public events the enforcement gateway cares about.
 *
 * <p>Messages on {@code public.event} are MSF {@code JsonEnvelope}s: a {@code _metadata} object (carrying
 * the event {@code name}) plus the payload. The microservice framework also copies the event name onto the
 * JMS string property {@code CPPNAME} (see {@code DefaultEnvelopeConverter}), which is what the broker-side
 * message selector filters on — so this listener never sees events other than the ones in its selector.
 *
 * <p>For the POC this parses the envelope and hands a {@link ConfirmedHearingEvent} to the sink. Production
 * will instead apply the enforcement filter (by prosecuting authority), enrich, and POST to Libra.
 */
@Slf4j
@Component
public class ListingPublicEventListener {

    private static final ObjectMapper MAPPER = new JsonMapper();

    private final ReceivedPublicEventSink sink;

    public ListingPublicEventListener(final ReceivedPublicEventSink sink) {
        this.sink = sink;
    }

    @JmsListener(
            destination = "${enforcementgateway.messaging.public-event-topic}",
            subscription = "${enforcementgateway.messaging.subscription-name}",
            selector = "${enforcementgateway.messaging.selector}",
            containerFactory = "publicEventTopicFactory")
    public void onPublicEvent(final TextMessage message) throws JMSException {
        final String cppName = message.getStringProperty("CPPNAME");
        final ConfirmedHearingEvent event = parse(cppName, message.getText());
        log.info("Consumed listing public event '{}' [hearingId={}, sittingDay={}, courtHearingLocation={}, "
                        + "caseId={}, isCivil={}]",
                event.eventName(), event.hearingId(), event.sittingDay(),
                event.courtHearingLocation(), event.firstCaseId(), event.isCivil());
        sink.record(event);
    }

    private ConfirmedHearingEvent parse(final String cppName, final String body) {
        ConfirmedHearingEvent event;
        try {
            final JsonNode root = MAPPER.readTree(body);
            // confirmed and updated events share the same hearing schema, under different wrapper keys.
            JsonNode hearing = root.path("confirmedHearing");
            if (hearing.isMissingNode()) {
                hearing = root.path("updatedHearing");
            }
            final JsonNode firstDay = hearing.path("hearingDays").path(0);
            final JsonNode firstCase = hearing.path("prosecutionCases").path(0);
            final JsonNode courtCentre = hearing.path("courtCentre");
            event = new ConfirmedHearingEvent(
                    text(root.path("_metadata").path("name"), cppName),
                    text(hearing.path("id"), null),
                    text(firstDay.path("sittingDay"), null),
                    text(courtCentre.path("code"), text(courtCentre.path("courtHearingLocation"), null)),
                    text(firstCase.path("id"), null),
                    firstCase.has("isCivil") ? firstCase.path("isCivil").asBoolean() : null);
        } catch (final JacksonException e) {
            log.warn("Could not parse listing public event body for CPPNAME={}", cppName, e);
            event = new ConfirmedHearingEvent(cppName, null, null, null, null, null);
        }
        return event;
    }

    private static String text(final JsonNode node, final String fallback) {
        return node.isMissingNode() || node.isNull() ? fallback : node.asText();
    }
}
