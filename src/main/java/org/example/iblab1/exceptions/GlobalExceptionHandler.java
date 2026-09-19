package org.example.iblab1.exceptions;

import org.example.iblab1.model.dto.response.ErrorMessageResponse;
import org.example.iblab1.security.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));

        return ResponseEntity.badRequest().body(new ErrorMessageResponse(message));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorMessageResponse(e.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleNotReadableException(HttpMessageNotReadableException e) {
        log.debug("Malformed request body", e);
        return ResponseEntity.badRequest().body(new ErrorMessageResponse("Malformed request body"));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleBadCredentialsException(BadCredentialsException e) {
        return ResponseEntity.status(401).body(createAndLogError("Bad credentials", e));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleUsernameNotFoundException(UsernameNotFoundException e) {
        return ResponseEntity.status(401).body(createAndLogError("Bad credentials", e));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleUnauthorizedException(UnauthorizedException e) {
        return ResponseEntity.status(401).body(createAndLogError(e.getMessage(), e));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleDisabledException(DisabledException e) {
        return ResponseEntity.status(403).body(createAndLogError("Account is disabled", e));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleAccessDeniedException(AccessDeniedException e) {
        return ResponseEntity.status(403).body(createAndLogError(e.getMessage(), e));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleNotFoundException(NotFoundException e) {
        return ResponseEntity.status(404).body(createAndLogError(e.getMessage(), e));
    }

    @ExceptionHandler
    public ResponseEntity<ErrorMessageResponse> handleOtherException(RuntimeException e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.internalServerError().body(new ErrorMessageResponse("Internal Server Error"));
    }

    private ErrorMessageResponse createAndLogError(String message, Throwable e) {
        log.warn(LogSanitizer.sanitize(message), e);
        return new ErrorMessageResponse(message);
    }
}
