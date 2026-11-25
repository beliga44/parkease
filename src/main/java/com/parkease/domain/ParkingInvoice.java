package com.parkease.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static com.parkease.domain.Guards.requireNonNegative;
import static com.parkease.domain.Guards.requireNonNull;
import static com.parkease.domain.Guards.requireNotBlank;

public class ParkingInvoice {

    private final String invoiceId;
    private final String reservationId;
    private final String customerId;
    private final long billedDurationMinutes;
    private final BigDecimal baseAmount;
    private final BigDecimal overnightSurchargeApplied;
    private final BigDecimal discountAmount;
    private final BigDecimal totalAmount;
    private final LocalDateTime generatedAt;

    public ParkingInvoice(
            String invoiceId, String reservationId, String customerId, long billedDurationMinutes,
            BigDecimal baseAmount, BigDecimal overnightSurchargeApplied, BigDecimal discountAmount,
            BigDecimal totalAmount, LocalDateTime generatedAt
    ) {
        this.invoiceId = requireNotBlank(invoiceId, "invoiceId");
        this.reservationId = requireNotBlank(reservationId, "reservationId");
        this.customerId = requireNotBlank(customerId, "customerId");
        this.billedDurationMinutes = requireNonNegative(billedDurationMinutes, "billedDurationMinutes");
        this.baseAmount = requireNonNegative(baseAmount, "baseAmount");
        this.overnightSurchargeApplied = requireNonNegative(overnightSurchargeApplied, "overnightSurchargeApplied");
        this.discountAmount = requireNonNegative(discountAmount, "discountAmount");
        this.totalAmount = requireNonNull(totalAmount, "totalAmount");
        this.generatedAt = requireNonNull(generatedAt, "generatedAt");
    }

    public String getInvoiceId() { return invoiceId; }
    public String getReservationId() { return reservationId; }
    public String getCustomerId() { return customerId; }
    public long getBilledDurationMinutes() { return billedDurationMinutes; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public BigDecimal getOvernightSurchargeApplied() { return overnightSurchargeApplied; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
}
