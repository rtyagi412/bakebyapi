package com.bakeby.bakebyapi.common;

import org.springframework.http.HttpStatus;

/** Request conflicts with current state (409). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "Conflict", message);
    }
}
