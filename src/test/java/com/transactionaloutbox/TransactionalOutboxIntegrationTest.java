package com.transactionaloutbox;

import com.transactionaloutbox.exampleapp.payment.domain.PaymentStatus;
import com.transactionaloutbox.exampleapp.payment.persistence.PaymentRepository;
import com.transactionaloutbox.library.Outbox;
import io.nats.client.JetStreamManagement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "outbox.leader=true",
                "outbox.dispatcher.poll-interval-ms=25"
        })
class TransactionalOutboxIntegrationTest {

    private static final String STREAM_NAME = "OUTBOX";
    private static final int NATS_PORT = 4222;

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("outbox")
            .withUsername("outbox")
            .withPassword("outbox");

    @Container
    static final GenericContainer<?> nats = new GenericContainer<>(
            DockerImageName.parse("nats:2-alpine"))
            .withCommand("-js")
            .withExposedPorts(NATS_PORT);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("nats.server.url", () -> "nats://" + nats.getHost() + ":" + nats.getMappedPort(NATS_PORT));
    }

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private Outbox outbox;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JetStreamManagement jetStreamManagement;

    @BeforeEach
    void resetState() throws Exception {
        jdbcTemplate.update("TRUNCATE TABLE payments, outbox");
        jdbcTemplate.update("UPDATE outbox_sequence SET last_value = 0 WHERE name = 'global'");
        jetStreamManagement.purgeStream(STREAM_NAME);
    }

    @Test
    void committedBusinessTransactionIsEventuallyPublished() {
        UUID paymentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            paymentRepository.insert(paymentId, new BigDecimal("42.50"), PaymentStatus.REQUESTED);
            outbox.stage("payment.requested", new PaymentEvent(paymentId));
        });

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(count("SELECT COUNT(*) FROM payments WHERE id = ?", paymentId)).isOne();
            assertThat(count("SELECT COUNT(*) FROM outbox WHERE published_at IS NOT NULL")).isOne();
            assertThat(streamMessageCount()).isOne();
        });
    }

    @Test
    void rolledBackBusinessTransactionIsNeverPublished() throws Exception {
        UUID paymentId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            paymentRepository.insert(paymentId, new BigDecimal("42.50"), PaymentStatus.REQUESTED);
            outbox.stage("payment.requested", new PaymentEvent(paymentId));
            status.setRollbackOnly();
        });

        // Allow several scheduled poll cycles to prove no committed row is visible.
        Thread.sleep(250);

        assertThat(count("SELECT COUNT(*) FROM payments WHERE id = ?", paymentId)).isZero();
        assertThat(count("SELECT COUNT(*) FROM outbox")).isZero();
        assertThat(streamMessageCount()).isZero();
    }

    @Test
    void messagesArePublishedInTheirTransactionalSequence() {
        transactionTemplate.executeWithoutResult(status -> {
            outbox.stage("payment.first", new OrderedEvent(1));
            outbox.stage("payment.second", new OrderedEvent(2));
        });

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(streamMessageCount()).isEqualTo(2));

        long firstSequence = streamFirstSequence();
        assertThat(messageSubject(firstSequence)).isEqualTo("payment.first");
        assertThat(messagePayload(firstSequence)).contains("\"order\":1");
        assertThat(messageSubject(firstSequence + 1)).isEqualTo("payment.second");
        assertThat(messagePayload(firstSequence + 1)).contains("\"order\":2");
    }

    private long count(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }

    private long streamMessageCount() throws Exception {
        return jetStreamManagement.getStreamInfo(STREAM_NAME).getStreamState().getMsgCount();
    }

    private long streamFirstSequence() {
        try {
            return jetStreamManagement.getStreamInfo(STREAM_NAME).getStreamState().getFirstSequence();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String messageSubject(long streamSequence) {
        try {
            return jetStreamManagement.getMessage(STREAM_NAME, streamSequence).getSubject();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String messagePayload(long streamSequence) {
        try {
            byte[] data = jetStreamManagement.getMessage(STREAM_NAME, streamSequence).getData();
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record PaymentEvent(UUID paymentId) {
    }

    private record OrderedEvent(int order) {
    }
}
