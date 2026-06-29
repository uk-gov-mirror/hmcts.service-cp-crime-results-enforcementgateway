package uk.gov.hmcts.cp.messaging;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.util.StringUtils;

/**
 * JMS wiring for consuming CP public events off the shared Artemis topic {@code public.event}.
 *
 * <p>A durable topic subscriber must set a {@code clientID} on a connection it owns; Spring Boot's
 * auto-configured connection factory is a shared/cached proxy that rejects {@code setClientID}. So this
 * defines a dedicated {@link ActiveMQConnectionFactory} for the listener (which also makes Spring Boot's
 * {@code ConnectionFactory} auto-configuration back off), plus a listener-container factory tuned for the
 * CPP public-event topic:
 * <ul>
 *   <li>{@code pubSubDomain = true} — {@code public.event} is a multicast topic, not a queue;</li>
 *   <li>{@code subscriptionDurable = true} + a stable {@code clientId} — so the subscription survives
 *       restarts and events published while the service is down are still delivered;</li>
 *   <li>{@code autoStartup} gated by {@code enforcementgateway.messaging.listener-enabled} (default
 *       {@code false}) — so the bare service and the actuator test start without needing a broker.</li>
 * </ul>
 */
@Configuration
@EnableJms
public class PublicEventJmsConfig {

    @Bean
    public ActiveMQConnectionFactory publicEventConnectionFactory(
            @Value("${spring.artemis.broker-url}") final String brokerUrl,
            @Value("${spring.artemis.user:}") final String user,
            @Value("${spring.artemis.password:}") final String password) {

        final ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl);
        if (StringUtils.hasText(user)) {
            connectionFactory.setUser(user);
        }
        if (StringUtils.hasText(password)) {
            connectionFactory.setPassword(password);
        }
        return connectionFactory;
    }

    @Bean
    public DefaultJmsListenerContainerFactory publicEventTopicFactory(
            @Qualifier("publicEventConnectionFactory") final ConnectionFactory connectionFactory,
            @Value("${enforcementgateway.messaging.client-id}") final String clientId,
            @Value("${enforcementgateway.messaging.listener-enabled:false}") final boolean listenerEnabled) {

        final DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setPubSubDomain(true);
        factory.setSubscriptionDurable(true);
        factory.setClientId(clientId);
        factory.setAutoStartup(listenerEnabled);
        return factory;
    }
}
