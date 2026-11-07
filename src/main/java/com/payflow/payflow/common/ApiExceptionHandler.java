package com.payflow.payflow.common;

import com.payflow.payflow.payment.InvalidStateTransitionException;
import com.payflow.payflow.payment.PaymentNotFoundException;
import com.payflow.payflow.provider.ProviderUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", detail);
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ProblemDetail notFound(PaymentNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ProblemDetail invalidTransition(InvalidStateTransitionException e) {
        return problem(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION", e.getMessage());
    }

    @ExceptionHandler(ProviderUnavailableException.class)
    public ProblemDetail providerDown(ProviderUnavailableException e) {
        return problem(HttpStatus.BAD_GATEWAY, "PROVIDER_UNAVAILABLE", e.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status.getReasonPhrase());
        pd.setProperty("code", code);
        return pd;
    }
}