package uk.gov.hmcts.cp.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import uk.gov.hmcts.cp.dto.ConfirmedHearing;

/**
 * Sends the {@code confirmedHearing} payload to Azure APIM, which forwards the same payload on to
 * Libra - this service never calls Libra directly. The base URL is a placeholder pending the real
 * APIM endpoint - see {@code api-cp-crime-results-enforcementgateway}'s OpenAPI spec, which is
 * still explicitly draft/unreconciled with Libra. Uses {@code libraRestClientBuilder} (see
 * {@link uk.gov.hmcts.cp.config.RestClientConfig}), which can present a mutual-TLS client
 * certificate to APIM - the pattern already established by {@code cpp-context-staging-
 * enforcement}'s equivalent Libra/APIM integration - once a real certificate is confirmed.
 */
@Slf4j
@Component
public class LibraClient {

    private final RestClient restClient;

    public LibraClient(@Qualifier("libraRestClientBuilder") final RestClient.Builder restClientBuilder,
                        @Value("${cp.libra.base-url}") final String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    /** Returns true if APIM accepted the callback (200/202) for onward delivery to Libra, false otherwise - never throws. */
    public boolean confirmHearing(final ConfirmedHearing confirmedHearing) {
        try {
            restClient.post()
                    .uri("/confirmedHearing")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(confirmedHearing)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (final RestClientException e) {
            log.error("Libra confirmedHearing callback (via APIM) failed for caseUrn {}", confirmedHearing.caseUrn(), e);
            return false;
        }
    }
}
