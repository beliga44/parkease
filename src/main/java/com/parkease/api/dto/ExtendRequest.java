package com.parkease.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ExtendRequest(@NotNull LocalDateTime newEndTime) {}
