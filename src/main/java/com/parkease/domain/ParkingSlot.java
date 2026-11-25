package com.parkease.domain;

import static com.parkease.domain.Guards.requireNonNull;
import static com.parkease.domain.Guards.requireNotBlank;

public class ParkingSlot {

    private final String slotId;
    private final VehicleType vehicleType;
    private ParkingSlotStatus status;
    private final int floorLevel;

    public ParkingSlot(String slotId, VehicleType vehicleType, int floorLevel) {
        if (floorLevel < 0) {
            throw new IllegalArgumentException("floorLevel must not be negative (0 = ground floor)");
        }

        this.slotId = requireNotBlank(slotId, "slotId");
        this.vehicleType = requireNonNull(vehicleType, "vehicleType");
        this.status = ParkingSlotStatus.AVAILABLE;
        this.floorLevel = floorLevel;
    }

    public String getSlotId() { return slotId; }
    public VehicleType getVehicleType() { return vehicleType; }
    public ParkingSlotStatus getStatus() { return status; }
    public int getFloorLevel() { return floorLevel; }

    public boolean isBookable() {
        return status.isBookable();
    }

    public void markAvailable() {
        status = ParkingSlotStatus.AVAILABLE;
    }

    public void markOccupied() {
        status = ParkingSlotStatus.OCCUPIED;
    }

    public void markReserved() {
        status = ParkingSlotStatus.RESERVED;
    }

    public void markMaintenance() {
        status = ParkingSlotStatus.MAINTENANCE;
    }
}
