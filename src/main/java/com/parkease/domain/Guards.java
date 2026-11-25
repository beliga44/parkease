package com.parkease.domain;

import java.math.BigDecimal;
import java.util.Objects;

final class Guards {

    private Guards() {
    }

    static String requireNotBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be empty");
        }
        return value;
    }

    static <T> T requireNonNull(T value, String field) {
        return Objects.requireNonNull(value, field + " is required");
    }

    static BigDecimal requireNonNegative(BigDecimal value, String field) {
        requireNonNull(value, field);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    static long requireNonNegative(long value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
