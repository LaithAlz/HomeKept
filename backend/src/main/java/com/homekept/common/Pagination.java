package com.homekept.common;

/**
 * Shared cursor-pagination clamp used by the admin/app list endpoints: a blank or
 * non-positive {@code limit} falls back to the endpoint's default page size, otherwise
 * it is capped at {@code maxSize} so a caller cannot request an unbounded page.
 */
public final class Pagination {

    private Pagination() {
    }

    public static int resolveLimit(Integer limit, int defaultSize, int maxSize) {
        if (limit == null || limit <= 0) {
            return defaultSize;
        }
        return Math.min(limit, maxSize);
    }
}
