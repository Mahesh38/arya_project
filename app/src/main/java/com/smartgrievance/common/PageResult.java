package com.smartgrievance.common;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalItems) {
    public PageResult {
        items = List.copyOf(items);
        if (page < 1 || size < 1 || size > 50 || totalItems < 0) {
            throw new IllegalArgumentException("Invalid page metadata");
        }
    }

    public long totalPages() { return Math.ceilDiv(totalItems, size); }
    public boolean hasPrevious() { return page > 1; }
    public boolean hasNext() { return page < totalPages(); }
    public long firstItemNumber() { return items.isEmpty() ? 0 : (page - 1L) * size + 1; }
    public long lastItemNumber() { return items.isEmpty() ? 0 : firstItemNumber() + items.size() - 1; }
}
