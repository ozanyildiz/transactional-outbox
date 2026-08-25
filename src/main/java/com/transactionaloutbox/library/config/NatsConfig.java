package com.transactionaloutbox.library.config;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class NatsConfig {

    private static final Logger log = LoggerFactory.getLogger(NatsConfig.class);
    private static final int STREAM_ALREADY_EXISTS = 10058;

    @Value("${nats.server.url:nats://localhost:4222}")
    private String serverUrl;

    @Value("${outbox.nats.stream-name:OUTBOX}")
    private String streamName;

    @Value("${outbox.nats.stream-subjects:payment.>}")
    private String subjects;

    @Bean(destroyMethod = "close")
    public Connection natsConnection() throws Exception {
        return Nats.connect(serverUrl);
    }

    @Bean
    public JetStream jetStream(Connection connection, JetStreamManagement jetStreamManagement) throws Exception {
        ensureStreamExists(jetStreamManagement);
        return connection.jetStream();
    }

    @Bean
    public JetStreamManagement jetStreamManagement(Connection connection) throws Exception {
        return connection.jetStreamManagement();
    }

    private void ensureStreamExists(JetStreamManagement jetStreamManagement) throws IOException, JetStreamApiException {
        if (jetStreamManagement.getStreamNames().contains(streamName)) {
            log.info("JetStream stream '{}' already exists; skipping creation", streamName);
            return;
        }
        StreamConfiguration config = StreamConfiguration.builder()
                .name(streamName)
                .subjects(subjects)
                .storageType(StorageType.File)
                .build();
        try {
            jetStreamManagement.addStream(config);
            log.info("Created JetStream stream '{}' for subjects {}", streamName, subjects);
        } catch (JetStreamApiException e) {
            if (e.getApiErrorCode() == STREAM_ALREADY_EXISTS) {
                log.info("JetStream stream '{}' was created concurrently by another instance", streamName);
            } else {
                throw e;
            }
        }
    }
}
