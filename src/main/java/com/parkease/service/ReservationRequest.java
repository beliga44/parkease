package com.parkease.service;

import com.parkease.domain.VehicleType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

public record ReservationRequest(
        @NotBlank @Pattern(regexp = "LOT-[A-Z0-9]{3,8}", message = "must match LOT-[A-Z0-9]{3,8}")
        String lotId,
        @NotNull VehicleType vehicleType,
        @NotBlank String licensePlate,
        @NotBlank String customerId,
        @NotNull LocalDateTime plannedStartTime,
        @NotNull LocalDateTime plannedEndTime
) {
    // Missing times return true here because @NotNull already reports them.
    @AssertTrue(message = "plannedEndTime must be after plannedStartTime")
    public boolean isEndAfterStart() {
        return plannedStartTime == null || plannedEndTime == null
                || plannedEndTime.isAfter(plannedStartTime);
    }
}
