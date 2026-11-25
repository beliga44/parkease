package com.parkease.service;

import com.parkease.domain.ParkingInvoice;
import com.parkease.domain.RateCard;
import com.parkease.domain.Reservation;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface BillingService {

    ParkingInvoice generateInvoice(
            Reservation reservation, RateCard rateCard);
    BigDecimal calculateAmount(
            LocalDateTime actualStartTime, LocalDateTime actualEndTime, RateCard rateCard);
}
