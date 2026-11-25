package com.parkease.api.dto;

import com.parkease.domain.ParkingSlot;
import com.parkease.domain.VehicleType;

public record SlotResponse(
        String slotId,
        int floorLevel,
        VehicleType
        vehicleType
) {
    public static SlotResponse from(ParkingSlot slot) {
        return new SlotResponse(slot.getSlotId(), slot.getFloorLevel(), slot.getVehicleType());
    }
}
