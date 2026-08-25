package com.transactionaloutbox.library.publisher;

import com.transactionaloutbox.library.model.OutboxMessage;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.PublishAck;
import io.nats.client.impl.Headers;
import io.nats.client.support.NatsJetStreamConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JetStreamNatsPublisherTest {

    private JetStream jetStream;
    private JetStreamNatsPublisher publisher;

    @BeforeEach
    void setUp() {
        jetStream = mock(JetStream.class);
        publisher = new JetStreamNatsPublisher(jetStream);
    }

    private static OutboxMessage aMessage() {
        return new OutboxMessage(UUID.randomUUID(), "payment.requested", "{\"foo\":\"bar\"}", Instant.now());
    }

    @Test
    void publishesToMessageSubjectWithPayloadBytes() throws Exception {
        OutboxMessage message = aMessage();
        when(jetStream.publish(any(), any(Headers.class), any(byte[].class))).thenReturn(mock(PublishAck.class));

        publisher.publish(message);

        var payloadCaptor = ArgumentCaptor.forClass(byte[].class);
        var headersCaptor = ArgumentCaptor.forClass(Headers.class);
        org.mockito.Mockito.verify(jetStream).publish(eq(message.subject()), headersCaptor.capture(), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isEqualTo(message.payload().getBytes(StandardCharsets.UTF_8));
        assertThat(headersCaptor.getValue().getFirst(NatsJetStreamConstants.MSG_ID_HDR)).isEqualTo(message.id().toString());
    }

    @Test
    void wrapsIOExceptionInUncheckedIOException() throws Exception {
        OutboxMessage message = aMessage();
        when(jetStream.publish(any(), any(Headers.class), any(byte[].class))).thenThrow(new IOException("connection lost"));

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrapsJetStreamApiExceptionInRuntimeException() throws Exception {
        OutboxMessage message = aMessage();
        JetStreamApiException apiException = mock(JetStreamApiException.class);
        when(jetStream.publish(any(), any(Headers.class), any(byte[].class))).thenThrow(apiException);

        assertThatThrownBy(() -> publisher.publish(message))
                .isInstanceOf(RuntimeException.class)
                .hasCause(apiException);
    }
}
