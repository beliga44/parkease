package com.parkease.api.dto;

public record CheckOutResponse(ReservationResponse reservation, InvoiceResponse invoice) {}