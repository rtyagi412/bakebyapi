package com.bakeby.bakebyapi.common.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTests {

    @Test
    void fromCopiesZeroBasedPageAndTotals() {
        PageImpl<String> src = new PageImpl<>(List.of("c"), PageRequest.of(2, 2), 5);
        PageResponse<String> r = PageResponse.from(src);
        assertThat(r.content()).containsExactly("c");
        assertThat(r.page()).isEqualTo(2);
        assertThat(r.size()).isEqualTo(2);
        assertThat(r.totalElements()).isEqualTo(5);
        assertThat(r.totalPages()).isEqualTo(3);
    }

    @Test
    void fromEmptyPageHasZeroTotals() {
        PageResponse<String> r = PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(r.content()).isEmpty();
        assertThat(r.page()).isZero();
        assertThat(r.totalElements()).isZero();
        assertThat(r.totalPages()).isZero();
    }
}
