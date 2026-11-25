package com.parkease.domain;

import java.math.BigDecimal;

import static com.parkease.domain.Guards.requireNonNegative;
import static com.parkease.domain.Guards.requireNonNull;

public class RateCard {
    public static final int DEFAULT_GRACE_PERIOD_MINUTES = 15;

    private final VehicleType vehicleType;
    private final BigDecimal hourlyRate;
    private final BigDecimal dailyCap;
    private final BigDecimal overnightSurcharge;
    private final int gracePeriodMinutes;

    public RateCard(VehicleType vehicleType, BigDecimal hourlyRate, BigDecimal dailyCap,
                    BigDecimal overnightSurcharge) {
        this(vehicleType, hourlyRate, dailyCap, overnightSurcharge, DEFAULT_GRACE_PERIOD_MINUTES);
    }

    public RateCard(VehicleType vehicleType, BigDecimal hourlyRate, BigDecimal dailyCap,
                    BigDecimal overnightSurcharge, int gracePeriodMinutes) {
        if (gracePeriodMinutes < 0) {
            throw new IllegalArgumentException("gracePeriodMinutes must not be negative");
        }

        this.vehicleType = requireNonNull(vehicleType, "vehicleType");
        this.hourlyRate = requireNonNegative(hourlyRate, "hourlyRate");
        this.dailyCap = requireNonNegative(dailyCap, "dailyCap");
        this.overnightSurcharge = requireNonNegative(overnightSurcharge, "overnightSurcharge");
        this.gracePeriodMinutes = gracePeriodMinutes;
    }

    public VehicleType getVehicleType() { return vehicleType; }
    public BigDecimal getHourlyRate() { return hourlyRate; }
    public BigDecimal getDailyCap() { return dailyCap; }
    public BigDecimal getOvernightSurcharge() { return overnightSurcharge; }
    public int getGracePeriodMinutes() { return gracePeriodMinutes; }
}
