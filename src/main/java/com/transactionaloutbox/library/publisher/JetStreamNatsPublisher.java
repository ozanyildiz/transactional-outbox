package com.transactionaloutbox.library.publisher;

import com.transactionaloutbox.library.model.OutboxMessage;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.support.NatsJetStreamConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Component
public class JetStreamNatsPublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(JetStreamNatsPublisher.class);

    private final JetStream jetStream;

    public JetStreamNatsPublisher(JetStream jetStream) {
        this.jetStream = jetStream;
    }

    @Override
    public void publish(OutboxMessage message) {
        Headers headers = new Headers();
        headers.add(NatsJetStreamConstants.MSG_ID_HDR, message.id().toString());
        try {
            PublishAck ack = jetStream.publish(message.subject(), headers, message.payload().getBytes(StandardCharsets.UTF_8));
            log.info("Message {} acknowledged by JetStream (stream seq {})", message.id(), ack.getSeqno());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (JetStreamApiException e) {
            throw new RuntimeException(e);
        }
    }
}
