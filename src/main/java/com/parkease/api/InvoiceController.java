package com.parkease.api;

import com.parkease.api.dto.InvoiceResponse;
import com.parkease.service.ReservationService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final ReservationService reservationService;

    public InvoiceController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/{reservationId}")
    public InvoiceResponse getInvoice(@PathVariable String reservationId) {
        return InvoiceResponse.from(reservationService.getInvoice(reservationId));
    }
}