package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientConfigTest {

    // Self-signed test certificate/key pair, generated for this test only - no relation to any
    // real Libra/APIM credential.
    private static final String TEST_CERTIFICATE_PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDGTCCAgGgAwIBAgIUbbRGiEBMka6ewjkxeY33rm6LO9gwDQYJKoZIhvcNAQEL
            BQAwHDEaMBgGA1UEAwwRbGlicmEtdGVzdC1jbGllbnQwHhcNMjYwODEwMTQ0NTA0
            WhcNMzYwODA3MTQ0NTA0WjAcMRowGAYDVQQDDBFsaWJyYS10ZXN0LWNsaWVudDCC
            ASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALL7z8IaRvroQ+pUAFuzMXks
            naYMLbPjIVW1aOsJ83B1H/W0iuImgaRK3FANvYns5izFXAAmoNCxLxnisw34Zdpc
            0lQzCJM977oKh2WYIcktOf8k7Y0m1jo7zm5uoSGQNhupzvT9l2iIxxNul4HeyY0a
            fZEwP2f9rA2HFuCrH6NG00Pjdbfmc+i/iLJi4wL4Mzt+8A8lWODB7oc3YEuY0mVK
            WiZRa/x5ClHXSFUCSoyFM5s+H99Se7Gg/uxaZr//Bek0Sp5tbJJzLWlYXiZyItsO
            NjC7sAT87NZvQ22V6RofSMFtTW9sKarwuTv2eecZQO1GohaGdoLG+msBM0s+yikC
            AwEAAaNTMFEwHQYDVR0OBBYEFKVbT9DiLDIyfJ6SpYSRkcVo5hZVMB8GA1UdIwQY
            MBaAFKVbT9DiLDIyfJ6SpYSRkcVo5hZVMA8GA1UdEwEB/wQFMAMBAf8wDQYJKoZI
            hvcNAQELBQADggEBAEs7usZQZdlwgF5y/cd3Aka2FdjBrZT2wvli3Jkil+h+oBOC
            sSDZzVAqGAobYsKSVJhgmDYdZrJUF0IsiEPbYtQQhtTwsHGvoBl99rr0vWf7ujIE
            2zwWmpvP/gOUKyN0RqvcQiESkNmQD37IZfXFA5L9kPRe0BMiN3/8xFvpmW3DkLsv
            r0ViyV/fVx4sEh8+J1CTRlgozXCT4LcSyc50XyykxntHUNZHfP+vrHXsjqGbmbox
            ifwxl7WklBmO8ZD6UYeHe0x4TAPt+mqELH6Co5duNJwwAXUsRvcHSqsnVptQfY2D
            RehWF/oAp6RyZkpJlFL+IWIXHlw4G40p0s8i7OQ=
            -----END CERTIFICATE-----
            """;

    private static final String TEST_PRIVATE_KEY_PEM = """
            -----BEGIN PRIVATE KEY-----
            MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCy+8/CGkb66EPq
            VABbszF5LJ2mDC2z4yFVtWjrCfNwdR/1tIriJoGkStxQDb2J7OYsxVwAJqDQsS8Z
            4rMN+GXaXNJUMwiTPe+6CodlmCHJLTn/JO2NJtY6O85ubqEhkDYbqc70/ZdoiMcT
            bpeB3smNGn2RMD9n/awNhxbgqx+jRtND43W35nPov4iyYuMC+DM7fvAPJVjgwe6H
            N2BLmNJlSlomUWv8eQpR10hVAkqMhTObPh/fUnuxoP7sWma//wXpNEqebWyScy1p
            WF4mciLbDjYwu7AE/OzWb0NtlekaH0jBbU1vbCmq8Lk79nnnGUDtRqIWhnaCxvpr
            ATNLPsopAgMBAAECggEABscoyHx57fwyv40mev0vupuDwtXY5eQ25HSGFcne2VuO
            IMi8A7Fv8mVQBOC9qfCkjNMn10JJ5Rf4pSh5HbvSO5vimS6vkfKOrAMZuoaxgi6q
            G21DaQzXSwWYcoUnqUo5SlXlZlCzgFFsXIzbxElyujBjy0ZCTcavp87TASIUhM3U
            SoV4nbqpvXKFS9q1aaDi61YkrA8N3nw3s8qSkcBQfgWieyOcK94qnqluVPuZqKQX
            JJxLEp0Noi8gm+ICIabwjUcSys0+/hDPURo5YWNa1v31uwn0uhmo5/tYjQotgYki
            vh/wXLXn8PEJSjQc03z88tXj0+pWFIIZbVIkLaoZ0wKBgQD1gmPGvZO3F5p5Z9Dw
            XFqxt6FuhwCSNAyi5ouThilzgEvkWq68ISIMcGAmJIfuV2zqPIxusrRorZFpxRwq
            M46tfPRMIAmoKX4YY3XGS6mErisVnF1KxNWcYZKqJ1KID1x65RJXOjmZY3CXjPeF
            UYOLDnNdORD+ZhQXe8OlTLEvgwKBgQC6obOfUBSX1/oxclnXYnRCJB5rqROb7qnT
            k+vEWSykZlj9oeKJ850m4hihy1Ri9Lz8tYSqZKqQdKOOCPB+ojr+/KnjGmqqQ4jS
            VCMUowY1gvOo6LVovBUOX+V3WZ1SHWqZiX2EqLtTAcp6gUa+kztFoKSK6BD8G6YV
            3UBA+pZj4wKBgDKwUB9pZsUnp2oniBkISGVm73qdfv2wp2c+yFSWH5rMQ18LZgZL
            pPcCgKd/ZV1NSZx3EduNI/h01ZAL9Uu1R2EEaoAJIVVJ89HfPyLI3mAZgaW9mflk
            +GGIN7rP0Zdr7IEnKIk/6UKFKGrx3Oz2rn0YZ7M4pAySUNWkZPmNAww3AoGAaNPo
            u4RUfNGQM3PiaKthV8FH4PrwC7brZu8AD6JzA8iFFbl3MDtIuw1l/oLh3E9RU7R1
            VuwCLe+F6oK06rwe3Rh4KBqvbQFP+avOpInJNAdg6zACrVhvj0pn7jjEt9nUBXeH
            rBrGVSJ9Y3/3h5XVRg+sPGWLHMA+8qr6q0TVcjcCgYAJsadcqRtWjc/rU/4jT1IJ
            lAWl08FGlHHDs2sWnVKBfP66nAb+2UZ0AAErps4QcGd2YxGmMmM4/q1WCy8xwe4I
            VYHwoKXj3XNIvdaiqo1vNWiMwLB4ouqkEpz6Aztf2eyhJd8lIk27DR/QDyj6o4+F
            1AXlFHV5XFoQNz0uAnIfIA==
            -----END PRIVATE KEY-----
            """;

    private final RestClientConfig config = new RestClientConfig();

    @Test
    void restClientBuilderIgnoresMutualTlsConfig() {
        final RestClient.Builder builder = config.restClientBuilder(5000, 10000);

        assertThat(builder).isNotNull();
    }

    @Test
    void libraRestClientBuilderWorksWhenMutualTlsDisabled() {
        final RestClient.Builder builder = config.libraRestClientBuilder(5000, 10000, false, "", "");

        assertThat(builder).isNotNull();
    }

    @Test
    void libraRestClientBuilderBuildsClientCertificateSslContextWhenMutualTlsEnabled() {
        final RestClient.Builder builder = config.libraRestClientBuilder(
                5000, 10000, true, TEST_CERTIFICATE_PEM, TEST_PRIVATE_KEY_PEM);

        assertThat(builder).isNotNull();
    }

    @Test
    void libraRestClientBuilderThrowsWhenMutualTlsEnabledWithInvalidCertificate() {
        assertThatThrownBy(() -> config.libraRestClientBuilder(5000, 10000, true, "not-a-certificate", TEST_PRIVATE_KEY_PEM))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mutual-TLS");
    }

    @Test
    void libraRestClientBuilderThrowsWhenMutualTlsEnabledWithInvalidPrivateKey() {
        assertThatThrownBy(() -> config.libraRestClientBuilder(5000, 10000, true, TEST_CERTIFICATE_PEM, "not-a-private-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mutual-TLS");
    }
}
