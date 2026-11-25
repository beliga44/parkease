package com.parkease.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CheckInRequest(@NotNull LocalDateTime actualStartTime) { }
