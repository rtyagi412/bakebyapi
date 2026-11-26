package com.bakeby.bakebyapi.common.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Request is invalid for reasons found by the service (400), reported as field errors in the same
 * {@code errors} map that Bean Validation failures use (for example an unknown {@code categoryId}).
 */
@Getter
public class FieldValidationException extends ApiException {

    private final Map<String, String> errors;

    public FieldValidationException(String field, String message) {
        super(HttpStatus.BAD_REQUEST, "Validation failed", "Validation failed");
        this.errors = Map.of(field, message);
    }
}
