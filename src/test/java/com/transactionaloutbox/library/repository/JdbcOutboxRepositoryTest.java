package com.transactionaloutbox.library.repository;

import com.transactionaloutbox.library.dispatcher.OutboxDispatchException;
import com.transactionaloutbox.library.model.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcOutboxRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private JdbcOutboxRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        repository = new JdbcOutboxRepository(jdbcTemplate, objectMapper);
    }

    @Test
    void allocatesSequenceThenInsertsSerializedPayloadWithGeneratedIdAndTimestamp() {
        Object payload = new Object();
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"foo\":\"bar\"}");
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        repository.add("payment.requested", payload);

        var allocateSqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(allocateSqlCaptor.capture(), eq(Long.class));
        assertThat(allocateSqlCaptor.getValue()).contains("UPDATE outbox_sequence");

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        var argsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture(), argsCaptor.capture(),
                argsCaptor.capture(), argsCaptor.capture(), argsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("INSERT INTO outbox");
        List<Object> args = argsCaptor.getAllValues();
        assertThat(args.get(0)).isInstanceOf(UUID.class);
        assertThat(args.get(1)).isEqualTo(42L);
        assertThat(args.get(2)).isEqualTo("payment.requested");
        assertThat(args.get(3)).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(args.get(4)).isInstanceOf(Timestamp.class);
    }

    @Test
    void marksMessagePublishedByIdWithTimestampWhenOneRowUpdated() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), any(Timestamp.class), eq(id))).thenReturn(1);

        repository.markPublished(id);

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Timestamp.class), eq(id));
        assertThat(sqlCaptor.getValue()).contains("UPDATE outbox");
        assertThat(sqlCaptor.getValue()).contains("SET published_at");
    }

    @Test
    void throwsWhenMarkPublishedUpdatesNoRows() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), any(Timestamp.class), eq(id))).thenReturn(0);

        assertThatThrownBy(() -> repository.markPublished(id))
                .isInstanceOf(OutboxDispatchException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void findOldestPendingOrdersBySequenceNo() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        repository.findOldestPending();

        var sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("ORDER BY sequence_no");
    }

    @Test
    void findOldestPendingReturnsEmptyWhenNoRowsMatch() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        Optional<OutboxMessage> result = repository.findOldestPending();

        assertThat(result).isEmpty();
    }

    @Test
    void findOldestPendingMapsRowToOutboxMessage() throws Exception {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("id")).thenReturn(id);
        when(rs.getString("type")).thenReturn("payment.requested");
        when(rs.getString("payload")).thenReturn("{\"foo\":\"bar\"}");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));

        ArgumentCaptor<RowMapper<OutboxMessage>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), mapperCaptor.capture())).thenAnswer(invocation -> {
            RowMapper<OutboxMessage> mapper = mapperCaptor.getValue();
            return List.of(mapper.mapRow(rs, 1));
        });

        Optional<OutboxMessage> result = repository.findOldestPending();

        assertThat(result).contains(new OutboxMessage(id, "payment.requested", "{\"foo\":\"bar\"}", createdAt));
    }
}
