package com.parkease.domain;

import java.time.LocalTime;
import java.util.Objects;

public record OperatingHours(LocalTime openTime, LocalTime closeTime) {

    public OperatingHours {
        Objects.requireNonNull(openTime, "openTime is required");
        Objects.requireNonNull(closeTime, "closeTime is required");
    }

    public static OperatingHours allDay() {
        return new OperatingHours(LocalTime.MIN, LocalTime.MAX);
    }
}
