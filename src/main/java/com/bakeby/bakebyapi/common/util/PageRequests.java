package com.bakeby.bakebyapi.common.util;

import com.bakeby.bakebyapi.common.exception.BadRequestException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Builds {@link Pageable} from raw page/size/sort request params. */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final String DEFAULT_SORT = "name,asc";

    private PageRequests() {
    }

    /** Single sort value; see {@link #of(Integer, Integer, List, Set)}. */
    public static Pageable of(Integer page, Integer size, String sort, Set<String> allowedSortFields) {
        return of(page, size, sort == null ? null : List.of(sort), allowedSortFields);
    }

    /**
     * Builds a {@link Pageable}. The property {@code id} is always appended as the final tiebreaker
     * sort key (ascending) so paging order is stable, even if {@code id} is not in
     * {@code allowedSortFields}. Every entity sorted through this method must therefore have an
     * {@code id} property.
     *
     * @param page   0-based page; null defaults to 0, negative throws {@link BadRequestException}
     * @param size   null defaults to 20, above 100 is clamped to 100, below 1 throws {@link BadRequestException}
     * @param sort   entries of "field" or "field,asc|desc"; null/blank means "name,asc"; {@code id} ascending is always appended last as a tiebreaker
     *               unless {@code id} is already sorted on
     * @param allowedSortFields whitelist of sortable fields; unknown field throws {@link BadRequestException}
     */
    public static Pageable of(Integer page, Integer size, List<String> sort, Set<String> allowedSortFields) {
        int p = page == null ? 0 : page;
        if (p < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        int s = size == null ? DEFAULT_SIZE : size;
        if (s < 1) {
            throw new BadRequestException("size must be >= 1");
        }
        s = Math.min(s, MAX_SIZE);

        boolean noSort = sort == null || sort.stream().allMatch(x -> x == null || x.isBlank());
        List<String> specs = noSort ? List.of(DEFAULT_SORT) : sort;
        List<Sort.Order> orders = new ArrayList<>();
        for (String spec : specs) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            String[] parts = spec.split(",", -1);
            String field = parts[0].trim();
            if (!allowedSortFields.contains(field)) {
                throw new BadRequestException("Unknown sort field '" + field + "'. Allowed: "
                        + allowedSortFields.stream().sorted().toList());
            }
            if (parts.length > 2) {
                throw new BadRequestException("Invalid sort '" + spec + "'");
            }
            Sort.Direction dir = Sort.Direction.ASC;
            if (parts.length == 2) {
                String d = parts[1].trim();
                if (d.equalsIgnoreCase("desc")) {
                    dir = Sort.Direction.DESC;
                } else if (!d.equalsIgnoreCase("asc")) {
                    throw new BadRequestException("Invalid sort direction '" + d + "'. Use asc or desc");
                }
            }
            orders.add(new Sort.Order(dir, field));
        }
        // Final tiebreaker so paging order is stable
        if (orders.stream().noneMatch(o -> o.getProperty().equals("id"))) {
            orders.add(Sort.Order.asc("id"));
        }
        return PageRequest.of(p, s, Sort.by(orders));
    }
}
