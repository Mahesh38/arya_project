package com.smartgrievance.common;

public record PageRequest(int page, int size) {
    public PageRequest {
        page = Math.max(1, page);
        size = Math.clamp(size, 1, 50);
    }

    public static PageRequest from(Integer page, Integer size) {
        return new PageRequest(page == null ? 1 : page, size == null ? 10 : size);
    }

    public PageRequest clampTo(long totalItems) {
        return new PageRequest((int) Math.min(page, Math.max(1, Math.ceilDiv(totalItems, size))), size);
    }

    public long offset() { return (page - 1L) * size; }
}
