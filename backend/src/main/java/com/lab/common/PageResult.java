package com.lab.common;

import java.util.List;

public record PageResult<T>(long total, List<T> records) {

    public static <T> PageResult<T> of(long total, List<T> records) {
        return new PageResult<>(total, records);
    }
}
