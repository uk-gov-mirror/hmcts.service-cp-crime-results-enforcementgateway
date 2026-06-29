package uk.gov.hmcts.cp.messaging;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * POC seam: records the listing public events the gateway consumes, so an integration test can await
 * and assert on them. In the real implementation this is replaced by the enforcement filter + Libra client.
 */
@Component
public class ReceivedPublicEventSink {

    private final List<ConfirmedHearingEvent> events = new CopyOnWriteArrayList<>();

    public void record(final ConfirmedHearingEvent event) {
        events.add(event);
    }

    public List<ConfirmedHearingEvent> received() {
        return List.copyOf(events);
    }

    public int count() {
        return events.size();
    }

    public void clear() {
        events.clear();
    }
}
