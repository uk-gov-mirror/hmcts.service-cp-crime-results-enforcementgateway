package uk.gov.hmcts.cp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyAcceptorFactory;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import uk.gov.hmcts.cp.messaging.ConfirmedHearingEvent;
import uk.gov.hmcts.cp.messaging.ReceivedPublicEventSink;

/**
 * POC / spike proof: a plain Spring Boot service can durably subscribe to the CP Artemis {@code public.event}
 * topic, filter to listing events via the {@code CPPNAME} message selector, and parse the {@code JsonEnvelope}
 * — all without the WildFly microservice framework.
 *
 * <p>Runs against a real (embedded) Artemis broker over TCP, mirroring the proven setup in
 * {@code cp-audit-filter-springboot}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.jms.listener.auto-startup=true")
class ListingPublicEventConsumptionIT {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final String TOPIC = "public.event";

    private static EmbeddedActiveMQ broker;
    private static int port;
    private static String sampleEnvelope;

    @Autowired
    private ReceivedPublicEventSink sink;

    @BeforeAll
    static void startBroker() throws Exception {
        sampleEnvelope = new String(Files.readAllBytes(
                java.nio.file.Path.of(Thread.currentThread().getContextClassLoader()
                        .getResource("events/public.listing.hearing-confirmed.sample.json").toURI())),
                StandardCharsets.UTF_8);

        port = findFreePort();
        final Configuration configuration = new ConfigurationImpl()
                .setSecurityEnabled(false)
                .setPersistenceEnabled(false)
                .setJMXManagementEnabled(false)
                .setJournalType(JournalType.NIO);
        configuration.setBindingsDirectory(Files.createTempDirectory("artemis-bindings").toString());
        configuration.setJournalDirectory(Files.createTempDirectory("artemis-journal").toString());
        configuration.setLargeMessagesDirectory(Files.createTempDirectory("artemis-large").toString());
        configuration.setPagingDirectory(Files.createTempDirectory("artemis-paging").toString());

        final Map<String, Object> params = new HashMap<>();
        params.put("host", "localhost");
        params.put("port", port);
        configuration.getAcceptorConfigurations().add(new TransportConfiguration(
                NettyAcceptorFactory.class.getName(), params, "tcp-acceptor"));

        broker = new EmbeddedActiveMQ();
        broker.setConfiguration(configuration);
        broker.start();
        broker.getActiveMQServer().waitForActivation(10, TimeUnit.SECONDS);
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (broker != null) {
            broker.stop();
        }
    }

    @DynamicPropertySource
    static void artemisProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.artemis.broker-url", () -> "tcp://localhost:" + port
                + "?ha=false&reconnectAttempts=2&initialConnectAttempts=10");
        registry.add("spring.artemis.user", () -> "");
        registry.add("spring.artemis.password", () -> "");
    }

    @BeforeEach
    void establishLiveSubscriptionAndReset() throws InterruptedException {
        // DefaultMessageListenerContainer registers its durable subscriber asynchronously, so publishing
        // immediately can race ahead of the subscription — and a message sent to a topic before the
        // subscription exists is simply dropped. Publish probe events until one is consumed (proving the
        // subscription is live and selector-filtering), then let in-flight probes settle and reset, so each
        // test starts from a known-live subscription with an empty sink.
        await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            if (sink.count() == 0) {
                publish("public.listing.hearing-confirmed");
            }
            return sink.count() > 0;
        });
        Thread.sleep(500);
        sink.clear();
    }

    @Test
    void durably_consumes_and_parses_a_hearing_confirmed_event() {
        publish("public.listing.hearing-confirmed");

        await().atMost(Duration.ofSeconds(15)).until(() -> sink.count() == 1);

        final ConfirmedHearingEvent event = sink.received().get(0);
        assertThat(event.eventName()).isEqualTo("public.listing.hearing-confirmed");
        assertThat(event.hearingId()).isEqualTo("4f9b9d2e-1111-4a22-9c33-aabbccddeeff");
        assertThat(event.sittingDay()).isEqualTo("2026-07-01T09:30:00.000Z");
        assertThat(event.courtHearingLocation()).isEqualTo("B01LY");
        assertThat(event.firstCaseId()).isEqualTo("7a2c1f00-2222-4b33-8c44-112233445566");
        assertThat(event.isCivil()).isTrue();
    }

    @Test
    void selector_filters_out_events_not_in_the_subscription() {
        // 'hearing-listed' is NOT in the CPPNAME selector and must be dropped by the broker;
        // 'hearing-updated' IS in the selector and acts as the positive control bounding the wait.
        publish("public.listing.hearing-listed");
        publish("public.listing.hearing-updated");

        await().atMost(Duration.ofSeconds(15)).until(() -> sink.count() == 1);

        assertThat(sink.received()).hasSize(1);
        assertThat(sink.received().get(0).eventName()).isEqualTo("public.listing.hearing-updated");
    }

    private void publish(final String eventName) {
        try {
            final ObjectNode root = (ObjectNode) MAPPER.readTree(sampleEnvelope);
            ((ObjectNode) root.get("_metadata")).put("name", eventName);
            final String body = MAPPER.writeValueAsString(root);

            final ActiveMQConnectionFactory factory =
                    new ActiveMQConnectionFactory("tcp://localhost:" + port);
            try (Connection connection = factory.createConnection()) {
                final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                final Topic topic = session.createTopic(TOPIC);
                final MessageProducer producer = session.createProducer(topic);
                final TextMessage message = session.createTextMessage(body);
                message.setStringProperty("CPPNAME", eventName);
                producer.send(message);
                session.close();
            }
            factory.close();
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to publish test event " + eventName, e);
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }
}
