package uk.gov.hmcts.cp.messaging;

/**
 * Minimal projection of a CP listing {@code confirmedHearing} / {@code updatedHearing} public event,
 * holding just the fields the enforcement gateway needs to map onto the Libra {@code confirmedHearing}
 * payload (FR09 / FR11). This is the POC shape — enrichment (caseURN, prosecuting authority) is out of scope.
 *
 * @param eventName            the public event name (e.g. {@code public.listing.hearing-confirmed})
 * @param hearingId            {@code confirmedHearing.id}
 * @param sittingDay           {@code confirmedHearing.hearingDays[0].sittingDay} (ISO date-time)
 * @param courtHearingLocation {@code confirmedHearing.courtCentre.code} (OU code)
 * @param firstCaseId          {@code confirmedHearing.prosecutionCases[0].id} (case UUID)
 * @param isCivil              {@code confirmedHearing.prosecutionCases[0].isCivil}
 */
public record ConfirmedHearingEvent(
        String eventName,
        String hearingId,
        String sittingDay,
        String courtHearingLocation,
        String firstCaseId,
        Boolean isCivil) {
}
