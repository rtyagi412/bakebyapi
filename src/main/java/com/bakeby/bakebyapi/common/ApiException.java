package com.bakeby.bakebyapi.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for domain exceptions mapped to HTTP errors by {@link GlobalExceptionHandler}. Subclasses supply the
 * status and title, so a new subclass is handled without touching the handler.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String message) {
        super(message);
        this.status = status;
        this.title = title;
    }
}
