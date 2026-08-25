package com.transactionaloutbox.library.repository;

import com.transactionaloutbox.library.dispatcher.OutboxDispatchException;
import com.transactionaloutbox.library.model.OutboxMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JdbcOutboxRepository implements OutboxRepository {

    private static final String ALLOCATE_SEQUENCE_SQL = """
            UPDATE outbox_sequence
            SET last_value = last_value + 1
            WHERE name = 'global'
            RETURNING last_value
            """;

    private static final String INSERT_SQL = """
            INSERT INTO outbox (id, sequence_no, type, payload, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

    private static final String MARK_PUBLISHED_SQL = """
            UPDATE outbox
            SET published_at = ?
            WHERE id = ?
              AND published_at IS NULL
            """;

    private static final String FIND_OLDEST_PENDING_SQL = """
            SELECT id, type, payload, created_at FROM outbox
            WHERE published_at IS NULL
            ORDER BY sequence_no
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void add(String type, Object payload) {
        long sequenceNo = jdbcTemplate.queryForObject(ALLOCATE_SEQUENCE_SQL, Long.class);
        jdbcTemplate.update(INSERT_SQL, UUID.randomUUID(), sequenceNo, type,
                objectMapper.writeValueAsString(payload), Timestamp.from(Instant.now()));
    }

    @Override
    public void markPublished(UUID id) {
        int updated = jdbcTemplate.update(MARK_PUBLISHED_SQL, Timestamp.from(Instant.now()), id);
        if (updated != 1) {
            throw new OutboxDispatchException("Could not mark outbox message as published: " + id);
        }
    }

    @Override
    public Optional<OutboxMessage> findOldestPending() {
        List<OutboxMessage> results = jdbcTemplate.query(FIND_OLDEST_PENDING_SQL, this::mapRow);
        return results.stream().findFirst();
    }

    private OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new OutboxMessage(
                (UUID) rs.getObject("id"),
                rs.getString("type"),
                rs.getString("payload"),
                rs.getTimestamp("created_at").toInstant());
    }
}
