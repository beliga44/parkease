package com.parkease.api.dto;

import com.parkease.domain.ParkingInvoice;

import java.time.LocalDateTime;

public record InvoiceResponse(
        String invoiceId,
        String reservationId,
        String customerId,
        long billedDurationMinutes,
        String baseAmount,
        String overnightSurchargeApplied,
        String discountAmount,
        String totalAmount,
        LocalDateTime generatedAt
) {
    public static InvoiceResponse from(ParkingInvoice invoice) {
        return new InvoiceResponse(
                invoice.getInvoiceId(),
                invoice.getReservationId(),
                invoice.getCustomerId(),
                invoice.getBilledDurationMinutes(),
                invoice.getBaseAmount().toPlainString(),
                invoice.getOvernightSurchargeApplied().toPlainString(),
                invoice.getDiscountAmount().toPlainString(),
                invoice.getTotalAmount().toPlainString(),
                invoice.getGeneratedAt());
    }
}
