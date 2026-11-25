package com.parkease.domain;

import java.time.LocalDateTime;

import static com.parkease.domain.Guards.requireNonNull;
import static com.parkease.domain.Guards.requireNotBlank;

public class Reservation {

    private final String reservationId;
    private final String lotId;
    private final String slotId;
    private final String licensePlate;
    private final String customerId;
    private final VehicleType vehicleType;
    private final LocalDateTime plannedStartTime;
    private LocalDateTime plannedEndTime;
    private LocalDateTime actualStartTime, actualEndTime;
    private ReservationStatus status;
    private String cancellationReason;
    private boolean lateCancellation;

    public Reservation(
            String reservationId,
            String lotId,
            String slotId,
            VehicleType vehicleType,
            String licensePlate,
            String customerId,
            LocalDateTime plannedStartTime,
            LocalDateTime plannedEndTime
    ) {
        requireNonNull(plannedStartTime, "plannedStartTime");
        requireNonNull(plannedEndTime, "plannedEndTime");
        if (!plannedEndTime.isAfter(plannedStartTime))
            throw new IllegalArgumentException("plannedEndTime must be after plannedStartTime");

        this.reservationId = requireNotBlank(reservationId, "reservationId");
        this.lotId = requireNotBlank(lotId, "lotId");
        this.slotId = requireNotBlank(slotId, "slotId");
        this.vehicleType = requireNonNull(vehicleType, "vehicleType");
        this.licensePlate = requireNotBlank(licensePlate, "licensePlate");
        this.customerId = requireNotBlank(customerId, "customerId");
        this.plannedStartTime = plannedStartTime;
        this.plannedEndTime = plannedEndTime;
        this.status = ReservationStatus.PENDING;
        this.lateCancellation = false;
    }

    public String getReservationId() { return reservationId; }
    public String getLotId() { return lotId; }
    public String getSlotId() { return slotId; }
    public String getLicensePlate() { return licensePlate; }
    public String getCustomerId() { return customerId; }
    public VehicleType getVehicleType() { return vehicleType; }
    public LocalDateTime getPlannedStartTime() { return plannedStartTime; }
    public LocalDateTime getPlannedEndTime() { return plannedEndTime; }
    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public LocalDateTime getActualEndTime() { return actualEndTime; }
    public ReservationStatus getStatus() { return status; }
    public String getCancellationReason() { return cancellationReason; }
    public boolean getLateCancellation() { return lateCancellation; }

    public void checkIn(LocalDateTime checkInTime) {
        requireTransitionTo(ReservationStatus.ACTIVE);
        requireNonNull(checkInTime, "checkInTime");

        actualStartTime = checkInTime;
        status = ReservationStatus.ACTIVE;
    }

    public void checkOut(LocalDateTime checkoutTime) {
        requireTransitionTo(ReservationStatus.COMPLETED);
        requireNonNull(checkoutTime, "checkoutTime");
        if (checkoutTime.isBefore(actualStartTime))
            throw new IllegalArgumentException("checkoutTime must not be before actualStartTime");

        actualEndTime = checkoutTime;
        status = ReservationStatus.COMPLETED;
    }

    public void cancel(String reason, boolean late) {
        if (status == ReservationStatus.ACTIVE)
            throw new IllegalStateException("Reservation " + reservationId
                    + " is ACTIVE (vehicle checked in); check out instead so the stay is billed");
        requireTransitionTo(ReservationStatus.CANCELLED);

        lateCancellation = late;
        cancellationReason = reason;
        status = ReservationStatus.CANCELLED;
    }

    public void extend(LocalDateTime extendUntilTime) {
        if (!status.canExtend())
            throw new IllegalStateException("Reservation " + reservationId + " cannot be extended while "
                    + status + "; only ACTIVE reservations can be extended");
        requireNonNull(extendUntilTime, "extendUntilTime");
        if (!extendUntilTime.isAfter(plannedEndTime))
            throw new IllegalArgumentException("extendUntilTime must be after plannedEndTime");

        plannedEndTime = extendUntilTime;
    }

    public void markNoShow() {
        requireTransitionTo(ReservationStatus.NO_SHOW);

        status = ReservationStatus.NO_SHOW;
    }

    public boolean blocksSlot() {
        return status.blocksSlot();
    }

    private void requireTransitionTo(ReservationStatus next) {
        if (!status.canTransitionTo(next))
            throw new IllegalStateException("Reservation " + reservationId + " cannot go from "
                    + status + " to " + next);
    }

    public boolean overlaps(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime.isBefore(getPlannedEndTime()) &&
                endTime.isAfter(getPlannedStartTime());
    }
}
