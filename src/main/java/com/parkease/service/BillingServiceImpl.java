package com.parkease.service;

import com.parkease.domain.ParkingInvoice;
import com.parkease.domain.RateCard;
import com.parkease.domain.Reservation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BillingServiceImpl implements BillingService {
    private static final long BLOCK_MINUTES = 30;
    private static final BigDecimal DISCOUNT = BigDecimal.ZERO;

    private final Clock clock;

    public BillingServiceImpl(Clock clock) {
        this.clock = clock;
    }

    @Override
    public ParkingInvoice generateInvoice(Reservation reservation, RateCard rateCard) {
        if (reservation.getVehicleType() != rateCard.getVehicleType()) {
            throw new IllegalStateException("Vehicle Type must be checked in a same type");
        }

        LocalDateTime start = reservation.getActualStartTime();
        LocalDateTime end = reservation.getActualEndTime();
        if (start == null || end == null) {
            throw new IllegalStateException("Reservation must be checked in and checked out before billing");
        }

        boolean isFree = isInGracePeriod(start, end, rateCard);
        long billedDurationMinutes = isFree ? 0 :
                Duration.between(start, end).toMinutes() - rateCard.getGracePeriodMinutes();
        BigDecimal baseAmount = isFree ? BigDecimal.ZERO :
                baseAmount(start, end, rateCard);
        BigDecimal overnightSurcharge = isFree ? BigDecimal.ZERO :
                appliedOvernightSurcharge(start, end, rateCard);
        BigDecimal totalAmount = calculateAmount(start, end, rateCard);

        return new ParkingInvoice(
                UUID.randomUUID().toString(),
                reservation.getReservationId(),
                reservation.getCustomerId(),
                billedDurationMinutes,
                money(baseAmount),
                money(overnightSurcharge),
                money(DISCOUNT),
                money(totalAmount),
                LocalDateTime.now(clock)
        );
    }

    @Override
    public BigDecimal calculateAmount(LocalDateTime actualStartTime, LocalDateTime actualEndTime, RateCard rateCard) {
        if (isInGracePeriod(actualStartTime, actualEndTime, rateCard)) {
            return money(BigDecimal.ZERO);
        }

        BigDecimal amount = baseAmount(actualStartTime, actualEndTime, rateCard)
                .add(appliedOvernightSurcharge(actualStartTime, actualEndTime, rateCard))
                .subtract(DISCOUNT);

        return money(amount);
    }

    private BigDecimal baseAmount(LocalDateTime actualStartTime, LocalDateTime actualEndTime,
                                  RateCard rateCard) {
        BigDecimal blockRate = rateCard.getHourlyRate().divide(BigDecimal.valueOf(2));
        BigDecimal totalBaseAmount = BigDecimal.ZERO;

        LocalDateTime current = actualStartTime.plusMinutes(rateCard.getGracePeriodMinutes());

        while (current.isBefore(actualEndTime)) {
            LocalDateTime nextMidnight = current.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime segmentEnd = actualEndTime.isBefore(nextMidnight) ? actualEndTime :
                    nextMidnight;

            long durationInMinutes = Duration.between(current, segmentEnd).toMinutes();
            long totalBlocks = durationInMinutes / BLOCK_MINUTES;
            if (durationInMinutes % BLOCK_MINUTES != 0) {
                totalBlocks++;
            }

            BigDecimal dayCharge = blockRate.multiply(BigDecimal.valueOf(totalBlocks));
            totalBaseAmount = totalBaseAmount.add(dayCharge.min(rateCard.getDailyCap()));
            current = segmentEnd;
        }

        return totalBaseAmount;
    }

    private BigDecimal appliedOvernightSurcharge(
            LocalDateTime actualStartTime, LocalDateTime actualEndTime, RateCard rateCard
    ) {
        long countMidnight = ChronoUnit.DAYS.between(actualStartTime.toLocalDate(),
                actualEndTime.minusNanos(1).toLocalDate());

        return rateCard.getOvernightSurcharge().multiply(BigDecimal.valueOf(countMidnight));
    }

    private boolean isInGracePeriod(
            LocalDateTime actualStartTime, LocalDateTime actualEndTime, RateCard rateCard
    ) {
        if (actualEndTime.isBefore(actualStartTime)) {
            throw new IllegalArgumentException("actualEndTime must not be before actualStartTime");
        }

        Duration parkingDuration = Duration.between(actualStartTime, actualEndTime);
        return parkingDuration.toMinutes() <= rateCard.getGracePeriodMinutes();
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
