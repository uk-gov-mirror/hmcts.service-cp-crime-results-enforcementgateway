package uk.gov.hmcts.cp.messaging;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import uk.gov.hmcts.cp.event.ConfirmedHearingEvent;
import uk.gov.hmcts.cp.service.EnforcementHearingConfirmationService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HearingAllocationEventListenerTest {

    private static final String INNER_HEARING_JSON =
            "\"courtCentre\":{\"code\":\"B01LY\"},"
                    + "\"hearingDays\":[{\"sittingDay\":\"2026-07-15T10:00:00Z\"}],"
                    + "\"prosecutionCases\":[{\"id\":\"5b1f6c1e-1111-4a2b-9c3d-000000000001\"}]";

    /** hearing-confirmed wraps the payload under "confirmedHearing". */
    private static final String CONFIRMED_HEARING_JSON = "{\"confirmedHearing\":{" + INNER_HEARING_JSON + "},\"sendNotificationToParties\":true}";
    /** hearing-updated wraps the identically-shaped payload under a different key, "updatedHearing". */
    private static final String UPDATED_HEARING_JSON =
            "{\"updatedHearing\":{" + INNER_HEARING_JSON + "},\"sendNotificationToParties\":true,\"isNotificationAllocationFieldUpdated\":true}";
    /** an update unrelated to allocation (e.g. judiciary, public-list-note) - must not trigger a callback. */
    private static final String UPDATED_HEARING_NO_ALLOCATION_CHANGE_JSON =
            "{\"updatedHearing\":{" + INNER_HEARING_JSON + "},\"sendNotificationToParties\":true,\"isNotificationAllocationFieldUpdated\":false}";

    @Mock
    private Message message;
    @Mock
    private EnforcementHearingConfirmationService confirmationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldParseConfirmedHearingBodyAndDelegateToConfirmationService() throws JMSException {
        when(message.getBody(String.class)).thenReturn(CONFIRMED_HEARING_JSON);

        new HearingAllocationEventListener(objectMapper, confirmationService).onHearingAllocationEvent(message);

        verify(confirmationService).processConfirmedHearing(any(ConfirmedHearingEvent.class));
    }

    @Test
    void shouldParseUpdatedHearingBodyAndDelegateToConfirmationService() throws JMSException {
        when(message.getBody(String.class)).thenReturn(UPDATED_HEARING_JSON);

        new HearingAllocationEventListener(objectMapper, confirmationService).onHearingAllocationEvent(message);

        verify(confirmationService).processConfirmedHearing(any(ConfirmedHearingEvent.class));
    }

    @Test
    void shouldNotDelegateWhenUpdatedHearingAllocationFieldsUnchanged() throws JMSException {
        when(message.getBody(String.class)).thenReturn(UPDATED_HEARING_NO_ALLOCATION_CHANGE_JSON);

        new HearingAllocationEventListener(objectMapper, confirmationService).onHearingAllocationEvent(message);

        verify(confirmationService, never()).processConfirmedHearing(any());
    }

    @Test
    void shouldNotDelegateWhenBodyHasNeitherWrapperKey() throws JMSException {
        when(message.getBody(String.class)).thenReturn("{\"somethingElse\":{}}");
        when(message.getStringProperty("CPPNAME")).thenReturn("public.listing.hearing-confirmed");

        new HearingAllocationEventListener(objectMapper, confirmationService).onHearingAllocationEvent(message);

        verify(confirmationService, never()).processConfirmedHearing(any());
    }

    @Test
    void shouldLogAndNotPropagateWhenBodyIsMalformed() throws JMSException {
        when(message.getBody(String.class)).thenReturn("not valid json");
        when(message.getStringProperty("CPPNAME")).thenReturn("public.listing.hearing-confirmed");

        final HearingAllocationEventListener listener = new HearingAllocationEventListener(objectMapper, confirmationService);

        assertThatCode(() -> listener.onHearingAllocationEvent(message)).doesNotThrowAnyException();
    }
}
