package com.transactionaloutbox.exampleapp.payment.http;

import com.transactionaloutbox.exampleapp.payment.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<CreatePaymentResponse> createPayment(@RequestBody CreatePaymentRequest request) {

        UUID paymentId = paymentService.requestPayment(request.amount());

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePaymentResponse(paymentId));
    }

    @PostMapping("/{paymentId}/succeed")
    public ResponseEntity<Void> succeedPayment(@PathVariable UUID paymentId) {

        paymentService.succeedPayment(paymentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{paymentId}/fail")
    public ResponseEntity<Void> failPayment(@PathVariable UUID paymentId) {

        paymentService.failPayment(paymentId);
        return ResponseEntity.noContent().build();
    }

    public record CreatePaymentRequest(BigDecimal amount) {
    }

    public record CreatePaymentResponse(UUID paymentId) {
    }
}
