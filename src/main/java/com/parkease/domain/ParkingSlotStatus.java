package com.parkease.domain;

public enum ParkingSlotStatus {
    AVAILABLE, OCCUPIED, RESERVED, MAINTENANCE;

    public boolean isBookable() {
        return this != MAINTENANCE;
    }
}
