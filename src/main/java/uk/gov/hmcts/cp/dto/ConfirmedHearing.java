package uk.gov.hmcts.cp.dto;

import java.time.LocalDate;

/**
 * Outbound payload sent to Libra. Mirrors the {@code ConfirmedHearing} schema in
 * {@code api-cp-crime-results-enforcementgateway} (all 4 fields required). Defined locally rather
 * than depending on that repo's generated artefact, since it isn't published yet (still draft
 * version, and its {@code EnforcementHearingApi} is a server-side controller contract, not a
 * client) - switch to the published dependency's model class once it's released.
 */
public record ConfirmedHearing(String caseUrn, String courtHearingLocation, LocalDate dateOfHearing,
                                String timeOfHearing) {
}
