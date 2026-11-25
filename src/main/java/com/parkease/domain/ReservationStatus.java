package com.parkease.domain;

public enum ReservationStatus {
    PENDING, ACTIVE, COMPLETED, CANCELLED, NO_SHOW;

    public boolean canTransitionTo(ReservationStatus next) {
        return switch (this) {
            case PENDING -> next == ACTIVE || next == CANCELLED || next == NO_SHOW;
            case ACTIVE -> next == COMPLETED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }

    public boolean blocksSlot() {
        return this == PENDING || this == ACTIVE;
    }

    public boolean canExtend() {
        return this == ACTIVE;
    }
}
