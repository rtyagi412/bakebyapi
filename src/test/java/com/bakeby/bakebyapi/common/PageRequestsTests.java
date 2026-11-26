package com.bakeby.bakebyapi.common;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestsTests {

    private static final Set<String> ALLOWED = Set.of("name", "createdAt", "price");

    private static Sort sortOf(Sort.Order... orders) {
        return Sort.by(orders);
    }

    @Test
    void exposesDocumentedConstants() {
        assertThat(PageRequests.DEFAULT_SIZE).isEqualTo(20);
        assertThat(PageRequests.MAX_SIZE).isEqualTo(100);
        assertThat(PageRequests.DEFAULT_SORT).isEqualTo("name,asc");
    }

    @Test
    void appliesDefaultsWhenAllNull() {
        Pageable p = PageRequests.of(null, null, (String) null, ALLOWED);
        assertThat(p.getPageNumber()).isZero();
        assertThat(p.getPageSize()).isEqualTo(20);
        assertThat(p.getSort()).isEqualTo(sortOf(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    @Test
    void blankSortFallsBackToNameAsc() {
        assertThat(PageRequests.of(0, 10, "  ", ALLOWED).getSort())
                .isEqualTo(sortOf(Sort.Order.asc("name"), Sort.Order.asc("id")));
        assertThat(PageRequests.of(0, 10, List.<String>of(), ALLOWED).getSort())
                .isEqualTo(sortOf(Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    @Test
    void clampsSizeAboveMaxToMax() {
        assertThat(PageRequests.of(0, 1000, "name", ALLOWED).getPageSize()).isEqualTo(100);
        assertThat(PageRequests.of(0, 100, "name", ALLOWED).getPageSize()).isEqualTo(100);
    }

    @Test
    void usesGivenPageAndSize() {
        Pageable p = PageRequests.of(3, 5, "name", ALLOWED);
        assertThat(p.getPageNumber()).isEqualTo(3);
        assertThat(p.getPageSize()).isEqualTo(5);
    }

    @Test
    void sortWithoutDirectionDefaultsToAscending() {
        assertThat(PageRequests.of(0, 5, "price", ALLOWED).getSort())
                .isEqualTo(sortOf(Sort.Order.asc("price"), Sort.Order.asc("id")));
    }

    @Test
    void parsesDirectionCaseInsensitively() {
        assertThat(PageRequests.of(0, 5, "price,DESC", ALLOWED).getSort())
                .isEqualTo(sortOf(Sort.Order.desc("price"), Sort.Order.asc("id")));
        assertThat(PageRequests.of(0, 5, "price,Asc", ALLOWED).getSort())
                .isEqualTo(sortOf(Sort.Order.asc("price"), Sort.Order.asc("id")));
    }

    @Test
    void supportsMultipleSortEntries() {
        Pageable p = PageRequests.of(0, 5, List.of("price,desc", "name"), ALLOWED);
        assertThat(p.getSort())
                .isEqualTo(sortOf(Sort.Order.desc("price"), Sort.Order.asc("name"), Sort.Order.asc("id")));
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> PageRequests.of(-1, 5, "name", ALLOWED)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsSizeBelowOne() {
        assertThatThrownBy(() -> PageRequests.of(0, 0, "name", ALLOWED)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> PageRequests.of(0, -3, "name", ALLOWED)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsSortFieldNotWhitelisted() {
        assertThatThrownBy(() -> PageRequests.of(0, 5, "secret,asc", ALLOWED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsBadDirection() {
        assertThatThrownBy(() -> PageRequests.of(0, 5, "name,up", ALLOWED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsMoreThanTwoCommaParts() {
        assertThatThrownBy(() -> PageRequests.of(0, 5, "name,asc,x", ALLOWED))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void appendsIdAscAsFinalTiebreaker() {
        Sort sort = PageRequests.of(0, 5, "price,desc", Set.of("price", "id")).getSort();
        assertThat(sort).containsExactly(Sort.Order.desc("price"), Sort.Order.asc("id"));
    }

    @Test
    void appendsIdAfterAllExplicitSortEntries() {
        Sort sort = PageRequests.of(0, 5, List.of("price,desc", "name"), ALLOWED).getSort();
        assertThat(sort).containsExactly(Sort.Order.desc("price"), Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    void appendsIdTiebreakerToDefaultSort() {
        assertThat(PageRequests.of(null, null, (String) null, ALLOWED).getSort())
                .containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
    }

    @Test
    void doesNotDuplicateIdWhenSortedByIdExplicitly() {
        Sort sort = PageRequests.of(0, 5, "id,desc", Set.of("id")).getSort();
        assertThat(sort).containsExactly(Sort.Order.desc("id"));
    }

    @Test
    void rejectsDefaultSortWhenNameIsNotWhitelisted() {
        assertThatThrownBy(() -> PageRequests.of(0, 5, (String) null, Set.of("price")))
                .isInstanceOf(BadRequestException.class);
    }
}
