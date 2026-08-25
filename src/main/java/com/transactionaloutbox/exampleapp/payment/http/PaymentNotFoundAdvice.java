package com.transactionaloutbox.exampleapp.payment.http;

import com.transactionaloutbox.exampleapp.payment.domain.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentNotFoundAdvice {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<String> handle(PaymentNotFoundException exception) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(exception.getMessage());
    }
}
