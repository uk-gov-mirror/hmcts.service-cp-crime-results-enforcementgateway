package uk.gov.hmcts.cp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;

@Configuration
public class RestClientConfig {

    private static final char[] IN_MEMORY_KEYSTORE_PASSWORD = "libra-client".toCharArray();

    /**
     * Prototype-scoped: {@link RestClient.Builder} is mutable ({@code baseUrl(...)} mutates in
     * place rather than returning a copy), so each client needs its own instance rather than
     * sharing (and clobbering) a singleton's base URL.
     *
     * <p>Explicit connect/read timeouts: these clients are called from inside a JMS listener
     * thread - an unbounded HTTP call (Progression, or Azure APIM fronting Libra, hanging) would
     * otherwise stall message processing indefinitely. Placeholder default values pending real
     * guidance on either SLA.
     */
    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder(
            @Value("${cp.http-client.connect-timeout-ms:5000}") final long connectTimeoutMs,
            @Value("${cp.http-client.read-timeout-ms:10000}") final long readTimeoutMs) {
        return buildRestClientBuilder(connectTimeoutMs, readTimeoutMs, null);
    }

    /**
     * Same as {@link #restClientBuilder}, but additionally presents a client certificate for
     * mutual TLS when {@code cp.libra.mutual-tls.enabled} is turned on. This mirrors the
     * established CP-to-Libra-via-APIM pattern already live in
     * {@code cpp-context-staging-enforcement}'s {@code GobClient} - that service authenticates to
     * Azure APIM's {@code CppGatewayService} API with a client certificate (private key sourced
     * from Azure Key Vault there). Disabled by default: no real certificate/key has been
     * confirmed for APIM's endpoint here yet, so this stays off (plain TLS, no client cert) until
     * {@code cp.libra.mutual-tls.enabled=true} and real PEM values are supplied.
     */
    @Bean(name = "libraRestClientBuilder")
    @Scope("prototype")
    public RestClient.Builder libraRestClientBuilder(
            @Value("${cp.http-client.connect-timeout-ms:5000}") final long connectTimeoutMs,
            @Value("${cp.http-client.read-timeout-ms:10000}") final long readTimeoutMs,
            @Value("${cp.libra.mutual-tls.enabled:false}") final boolean mutualTlsEnabled,
            @Value("${cp.libra.mutual-tls.client-certificate:}") final String clientCertificatePem,
            @Value("${cp.libra.mutual-tls.client-private-key:}") final String clientPrivateKeyPem) {
        final SSLContext sslContext = mutualTlsEnabled
                ? buildClientCertificateSslContext(clientCertificatePem, clientPrivateKeyPem)
                : null;
        return buildRestClientBuilder(connectTimeoutMs, readTimeoutMs, sslContext);
    }

    private RestClient.Builder buildRestClientBuilder(final long connectTimeoutMs, final long readTimeoutMs,
                                                        final SSLContext sslContext) {
        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs));
        if (sslContext != null) {
            httpClientBuilder.sslContext(sslContext);
        }
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClientBuilder.build());
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder().requestFactory(requestFactory);
    }

    /**
     * Builds an SSLContext presenting the given PEM certificate/private key as the mutual-TLS
     * client identity - the same PEM-into-an-in-memory-KeyStore approach as
     * {@code GobClient.loadTemporaryJKS()} in {@code cpp-context-staging-enforcement}, using
     * plain JDK crypto APIs (no Bouncy Castle - nothing else in this codebase needs it).
     */
    private SSLContext buildClientCertificateSslContext(final String certificatePem, final String privateKeyPem) {
        try {
            final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            final Certificate certificate = certificateFactory.generateCertificate(
                    new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.UTF_8)));

            final byte[] keyBytes = Base64.getDecoder().decode(stripPemHeaders(privateKeyPem));
            final PrivateKey privateKey = KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

            final KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, IN_MEMORY_KEYSTORE_PASSWORD);
            keyStore.setKeyEntry("libra-client", privateKey, IN_MEMORY_KEYSTORE_PASSWORD, new Certificate[]{certificate});

            final KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, IN_MEMORY_KEYSTORE_PASSWORD);

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            return sslContext;
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to build mutual-TLS SSLContext for the Libra/APIM client certificate", e);
        }
    }

    private String stripPemHeaders(final String pem) {
        return pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }
}
