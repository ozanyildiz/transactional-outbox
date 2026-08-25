package com.transactionaloutbox.exampleapp.payment.domain;

import java.util.UUID;

public record PaymentFailed(UUID paymentId) {
}
