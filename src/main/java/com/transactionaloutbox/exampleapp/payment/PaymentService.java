package com.transactionaloutbox.exampleapp.payment;

import com.transactionaloutbox.exampleapp.payment.domain.PaymentFailed;
import com.transactionaloutbox.exampleapp.payment.domain.PaymentRequested;
import com.transactionaloutbox.exampleapp.payment.domain.PaymentStatus;
import com.transactionaloutbox.exampleapp.payment.domain.PaymentSucceeded;
import com.transactionaloutbox.exampleapp.payment.persistence.PaymentRepository;
import com.transactionaloutbox.library.Outbox;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Outbox outbox;

    public PaymentService(PaymentRepository paymentRepository, Outbox outbox) {

        this.paymentRepository = paymentRepository;
        this.outbox = outbox;
    }

    @Transactional
    public UUID requestPayment(BigDecimal amount) {
        UUID paymentId = UUID.randomUUID();

        paymentRepository.insert(paymentId, amount, PaymentStatus.REQUESTED);

        outbox.stage("payment.requested", new PaymentRequested(paymentId, amount));

        return paymentId;
    }

    @Transactional
    public void succeedPayment(UUID paymentId) {
        paymentRepository.updateStatus(paymentId, PaymentStatus.SUCCEEDED);

        outbox.stage("payment.succeeded", new PaymentSucceeded(paymentId));
    }

    @Transactional
    public void failPayment(UUID paymentId) {
        paymentRepository.updateStatus(paymentId, PaymentStatus.FAILED);

        outbox.stage("payment.failed", new PaymentFailed(paymentId));
    }
}
