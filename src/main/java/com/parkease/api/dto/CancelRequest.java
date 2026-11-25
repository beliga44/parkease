package com.parkease.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelRequest(@NotBlank String reason) {}
