package com.example.aicustomerservice.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 管理端分页 VO（加法）：list + total + page + size，meta 供页面统计 chips。
 */
@Getter
@AllArgsConstructor
public class PageVO<T> {

    private final long total;
    private final int page;
    private final int size;
    private final List<T> list;
    private final Map<String, Object> meta;

    public static <T> PageVO<T> of(long total, int page, int size, List<T> list) {
        return new PageVO<>(total, page, size, list, Map.of());
    }

    public static <T> PageVO<T> of(long total, int page, int size, List<T> list, Map<String, Object> meta) {
        return new PageVO<>(total, page, size, list, meta);
    }
}