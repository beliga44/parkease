package com.parkease.service;

import com.parkease.domain.ParkingInvoice;
import com.parkease.domain.RateCard;
import com.parkease.domain.Reservation;
import com.parkease.domain.VehicleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BillingServiceImplTest {

    private static final LocalDateTime GENERATED_AT = LocalDateTime.parse("2024-03-20T00:00:00");

    private static final RateCard CAR_RATE = new RateCard(VehicleType.CAR,
            new BigDecimal("5000"), new BigDecimal("50000"), new BigDecimal("20000"));
    private static final RateCard TRUCK_RATE = new RateCard(VehicleType.TRUCK,
            new BigDecimal("15000"), new BigDecimal("120000"), new BigDecimal("30000"));
    private static final RateCard MOTORCYCLE_RATE = new RateCard(VehicleType.MOTORCYCLE,
            new BigDecimal("3000"), new BigDecimal("30000"), new BigDecimal("10000"));

    private final BillingServiceImpl billingService = new BillingServiceImpl(
            Clock.fixed(GENERATED_AT.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    private static Reservation completedReservation(VehicleType type, String checkIn, String checkOut) {
        LocalDateTime start = LocalDateTime.parse(checkIn);
        LocalDateTime end = LocalDateTime.parse(checkOut);
        Reservation reservation = new Reservation("RES-TEST", "LOT-JKT01", "A-01", type,
                "B 1234 XYZ", "CUST-001", start, end);
        reservation.checkIn(start);
        reservation.checkOut(end);
        return reservation;
    }

    @Test
    @DisplayName("Case A: 3h45m car stay minus 15m grace = 7 blocks = Rp 17,500.00")
    void standardHourlyBilling() {
        ParkingInvoice invoice = billingService.generateInvoice(
                completedReservation(VehicleType.CAR, "2024-03-15T09:00", "2024-03-15T12:45"), CAR_RATE);

        assertThat(invoice.getBilledDurationMinutes()).isEqualTo(210);
        assertThat(invoice.getBaseAmount()).isEqualTo(new BigDecimal("17500.00"));
        assertThat(invoice.getOvernightSurchargeApplied()).isEqualTo(new BigDecimal("0.00"));
        assertThat(invoice.getTotalAmount()).isEqualTo(new BigDecimal("17500.00"));
        assertThat(invoice.getGeneratedAt()).isEqualTo(GENERATED_AT);
    }

    @Test
    @DisplayName("Case B: truck over midnight caps each day at Rp 120,000 and adds one surcharge")
    void dailyCapAndOvernightSurcharge() {
        ParkingInvoice invoice = billingService.generateInvoice(
                completedReservation(VehicleType.TRUCK, "2024-03-15T07:00", "2024-03-16T09:00"), TRUCK_RATE);

        assertThat(invoice.getBaseAmount()).isEqualTo(new BigDecimal("240000.00"));
        assertThat(invoice.getOvernightSurchargeApplied()).isEqualTo(new BigDecimal("30000.00"));
        assertThat(invoice.getTotalAmount()).isEqualTo(new BigDecimal("270000.00"));
    }

    @Test
    @DisplayName("Case C: 12m motorcycle stay is inside the 15m grace period, so Rp 0.00")
    void stayWithinGracePeriodIsFree() {
        ParkingInvoice invoice = billingService.generateInvoice(
                completedReservation(VehicleType.MOTORCYCLE, "2024-03-15T14:00", "2024-03-15T14:12"), MOTORCYCLE_RATE);

        assertThat(invoice.getBilledDurationMinutes()).isZero();
        assertThat(invoice.getTotalAmount()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("Exactly 60m after grace = 2 full blocks, no rounding up")
    void exactBlockBoundaryIsNotRoundedUp() {
        ParkingInvoice invoice = billingService.generateInvoice(
                completedReservation(VehicleType.CAR, "2024-03-15T09:00", "2024-03-15T10:15"), CAR_RATE);

        assertThat(invoice.getBilledDurationMinutes()).isEqualTo(60);
        assertThat(invoice.getTotalAmount()).isEqualTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("61m after grace = partial third block rounded up to 3 blocks")
    void partialBlockIsRoundedUp() {
        ParkingInvoice invoice = billingService.generateInvoice(
                completedReservation(VehicleType.CAR, "2024-03-15T09:00", "2024-03-15T10:16"), CAR_RATE);

        assertThat(invoice.getBilledDurationMinutes()).isEqualTo(61);
        assertThat(invoice.getTotalAmount()).isEqualTo(new BigDecimal("7500.00"));
    }

    @Test
    @DisplayName("3-day stay: 2 midnight crossings = surcharge twice, middle day capped "
            + "(day 1: 8 blocks = 20,000, day 2: 48 blocks capped at 50,000, day 3: 16 blocks = 40,000)")
    void twoMidnightCrossingsApplySurchargeTwice() {
        ParkingInvoice invoice = billingService.generateInvoice(
                completedReservation(VehicleType.CAR, "2024-03-15T20:00", "2024-03-17T08:00"), CAR_RATE);

        assertThat(invoice.getBaseAmount()).isEqualTo(new BigDecimal("110000.00"));
        assertThat(invoice.getOvernightSurchargeApplied()).isEqualTo(new BigDecimal("40000.00"));
        assertThat(invoice.getTotalAmount()).isEqualTo(new BigDecimal("150000.00"));
    }

    private BigDecimal carAmount(String checkIn, String checkOut) {
        return billingService.calculateAmount(LocalDateTime.parse(checkIn), LocalDateTime.parse(checkOut), CAR_RATE);
    }

    @Test
    @DisplayName("Stay of exactly 15m equals the grace period, so Rp 0.00 (remaining duration <= 0)")
    void stayEqualToGracePeriodIsFree() {
        assertThat(carAmount("2024-03-15T09:00", "2024-03-15T09:15")).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("16m stay leaves 1m after grace, rounded up to 1 full block = Rp 2,500.00")
    void oneMinuteAfterGraceIsOneBlock() {
        assertThat(carAmount("2024-03-15T09:00", "2024-03-15T09:16")).isEqualTo(new BigDecimal("2500.00"));
    }

    @Test
    @DisplayName("Single-day stay over the cap is charged the daily cap only "
            + "(15h45m = 32 blocks = 80,000, capped at 50,000)")
    void singleDayStayIsCapped() {
        assertThat(carAmount("2024-03-15T06:00", "2024-03-15T22:00")).isEqualTo(new BigDecimal("50000.00"));
    }

    @Test
    @DisplayName("Leaving at exactly 00:00 is not a midnight crossing; leaving at 00:01 is")
    void leavingAtMidnightAddsNoSurcharge() {
        ParkingInvoice leavesAtMidnight = billingService.generateInvoice(
                completedReservation(VehicleType.CAR, "2024-03-15T20:00", "2024-03-16T00:00"), CAR_RATE);

        assertThat(leavesAtMidnight.getOvernightSurchargeApplied()).isEqualTo(new BigDecimal("0.00"));
        assertThat(leavesAtMidnight.getTotalAmount()).isEqualTo(new BigDecimal("20000.00"));

        // 8 blocks on day 1 (20,000) + 1 block on day 2 (2,500) + one surcharge (20,000)
        assertThat(carAmount("2024-03-15T20:00", "2024-03-16T00:01")).isEqualTo(new BigDecimal("42500.00"));
    }
}

