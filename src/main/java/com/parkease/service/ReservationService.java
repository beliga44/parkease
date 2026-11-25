package com.parkease.service;

import com.parkease.domain.ParkingInvoice;
import com.parkease.domain.ParkingSlot;
import com.parkease.domain.Reservation;
import com.parkease.domain.VehicleType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReservationService {
    Optional<ParkingSlot> findAvailableSlot(
            String lotId,
            VehicleType vehicleType,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    Reservation createReservation(ReservationRequest request);
    Reservation checkIn(String reservationId, LocalDateTime actualStartTime);
    Reservation checkOut(String reservationId, LocalDateTime actualEndTime);
    Reservation cancelReservation(String reservationId, String reason);
    Reservation extendReservation(String reservationId, LocalDateTime newEndTime);

    List<ParkingSlot> findAvailableSlots(String lotId, VehicleType vehicleType,
                                         LocalDateTime startTime, LocalDateTime endTime);
    ParkingSlot getSlot(String lotId, String slotId);
    ParkingInvoice getInvoice(String reservationId);
}
