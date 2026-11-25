package com.parkease.api;

import com.parkease.api.dto.*;
import com.parkease.domain.Reservation;
import com.parkease.service.ReservationRequest;
import com.parkease.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationRequest request) {
        return toResponse(reservationService.createReservation(request));
    }

    @PostMapping("/{reservationId}/check-in")
    public ReservationResponse checkIn(@PathVariable String reservationId,
                                       @Valid @RequestBody CheckInRequest request) {
        return toResponse(reservationService.checkIn(reservationId, request.actualStartTime()));
    }

    @PostMapping("/{reservationId}/check-out")
    public CheckOutResponse checkOut(@PathVariable String reservationId,
                                     @Valid @RequestBody CheckOutRequest request) {
        Reservation reservation = reservationService.checkOut(reservationId, request.actualEndTime());
        return new CheckOutResponse(
                toResponse(reservation),
                InvoiceResponse.from(reservationService.getInvoice(reservationId)));
    }

    @DeleteMapping("/{reservationId}")
    public ReservationResponse cancel(@PathVariable String reservationId,
                                      @Valid @RequestBody CancelRequest request) {
        return toResponse(reservationService.cancelReservation(reservationId, request.reason()));
    }

    @PutMapping("/{reservationId}/extend")
    public ReservationResponse extend(@PathVariable String reservationId,
                                      @Valid @RequestBody ExtendRequest request) {
        return toResponse(reservationService.extendReservation(reservationId, request.newEndTime()));
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return ReservationResponse.from(reservation,
                reservationService.getSlot(reservation.getLotId(), reservation.getSlotId()));
    }
}
