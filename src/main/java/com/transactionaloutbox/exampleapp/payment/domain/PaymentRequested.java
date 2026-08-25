package com.transactionaloutbox.exampleapp.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequested(UUID paymentId, BigDecimal amount) {
}
