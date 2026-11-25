package com.parkease.api;

import com.parkease.api.dto.SlotResponse;
import com.parkease.domain.VehicleType;
import com.parkease.service.ReservationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/lots")
public class LotController {

    private final ReservationService reservationService;

    public LotController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{lotId}/availability")
    public List<SlotResponse> availability(
            @PathVariable String lotId,
            @RequestParam VehicleType vehicleType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return reservationService.findAvailableSlots(lotId, vehicleType, startTime, endTime).stream()
                .map(SlotResponse::from)
                .toList();
    }
}