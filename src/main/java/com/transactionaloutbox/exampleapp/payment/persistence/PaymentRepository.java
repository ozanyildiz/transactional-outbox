package com.transactionaloutbox.exampleapp.payment.persistence;

import com.transactionaloutbox.exampleapp.payment.domain.PaymentNotFoundException;
import com.transactionaloutbox.exampleapp.payment.domain.PaymentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(
            UUID paymentId,
            BigDecimal amount,
            PaymentStatus status) {

        jdbcTemplate.update(
                """
                INSERT INTO payments (id, amount, status)
                VALUES (?, ?, ?)
                """,
                paymentId,
                amount,
                status.name()
        );
    }

    public void updateStatus(
            UUID paymentId,
            PaymentStatus status) {

        int updatedRows = jdbcTemplate.update(
                """
                UPDATE payments
                SET status = ?
                WHERE id = ?
                """,
                status.name(),
                paymentId
        );

        if (updatedRows == 0) {
            throw new PaymentNotFoundException(paymentId);
        }
    }
}
