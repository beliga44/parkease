package com.parkease.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.parkease.domain.ParkingSlot;
import com.parkease.domain.Reservation;
import com.parkease.domain.ReservationStatus;
import com.parkease.domain.VehicleType;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReservationResponse(
        String reservationId,
        String lotId,
        String slotId,
        int floorLevel,
        VehicleType vehicleType,
        String licensePlate,
        String customerId,
        ReservationStatus status,
        LocalDateTime plannedStartTime,
        LocalDateTime plannedEndTime,
        LocalDateTime actualStartTime,
        LocalDateTime actualEndTime,
        String cancellationReason,
        boolean lateCancellation
) {
    public static ReservationResponse from(Reservation reservation, ParkingSlot slot) {
        return new ReservationResponse(
                reservation.getReservationId(),
                reservation.getLotId(),
                reservation.getSlotId(),
                slot.getFloorLevel(),
                reservation.getVehicleType(),
                reservation.getLicensePlate(),
                reservation.getCustomerId(),
                reservation.getStatus(),
                reservation.getPlannedStartTime(),
                reservation.getPlannedEndTime(),
                reservation.getActualStartTime(),
                reservation.getActualEndTime(),
                reservation.getCancellationReason(),
                reservation.getLateCancellation());
    }
}
