package com.bakeby.bakebyapi.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionTests {

    @Test
    void notFoundExceptionExposesNotFoundStatusAndTitle() {
        var ex = new NotFoundException("missing");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ex.getTitle()).isEqualTo("Not found");
        assertThat(ex.getMessage()).isEqualTo("missing");
    }

    @Test
    void conflictExceptionExposesConflictStatusAndTitle() {
        var ex = new ConflictException("dup");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getTitle()).isEqualTo("Conflict");
        assertThat(ex.getMessage()).isEqualTo("dup");
    }

    @Test
    void badRequestExceptionExposesBadRequestStatusAndTitle() {
        var ex = new BadRequestException("bad");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getTitle()).isEqualTo("Bad request");
        assertThat(ex.getMessage()).isEqualTo("bad");
    }

    @Test
    void customSubclassExposesItsOwnStatusAndTitle() {
        var ex = new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Custom", "msg") {
        };
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ex.getTitle()).isEqualTo("Custom");
        assertThat(ex.getMessage()).isEqualTo("msg");
    }
}
