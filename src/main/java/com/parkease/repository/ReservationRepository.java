package com.parkease.repository;

import com.parkease.domain.Reservation;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ReservationRepository {

    private final ConcurrentHashMap<String, Reservation> reservations = new ConcurrentHashMap<>();

    public Reservation save(Reservation reservation) {
        reservations.put(reservation.getReservationId(), reservation);
        return reservation;
    }

    public Optional<Reservation> findReservationById(String id) {
        return Optional.ofNullable(reservations.get(id));
    }

    public List<Reservation> findReservationBySlot(String lotId, String slotId) {
        return reservations.values().stream()
                .filter(r -> r.getLotId().equals(lotId) && r.getSlotId().equals(slotId))
                .toList();
    }

    public boolean hasNoConflictReservation(String lotId, String slotId, String reservationId,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        // Exclude own reservation
        // Find only active or pending reservation
        // Check if reservation is colliding with window
        return findReservationBySlot(lotId, slotId).stream()
                .filter(r -> !r.getReservationId().equals(reservationId))
                .filter(Reservation::blocksSlot)
                .noneMatch(r -> r.overlaps(startTime, endTime));
    }
}
