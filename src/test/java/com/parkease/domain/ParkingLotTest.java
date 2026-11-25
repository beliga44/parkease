package com.parkease.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParkingLotTest {

    private static ParkingLot lotWith(Map<VehicleType, List<ParkingSlot>> slots) {
        return new ParkingLot("LOT-TEST1", "Test Lot", "Jakarta", slots, OperatingHours.allDay());
    }

    @Test
    void changesToCallerMapDoNotReachLot() {
        List<ParkingSlot> carSlots = new ArrayList<>(List.of(new ParkingSlot("A-01", VehicleType.CAR, 0)));
        Map<VehicleType, List<ParkingSlot>> slots = new HashMap<>(Map.of(VehicleType.CAR, carSlots));
        ParkingLot lot = lotWith(slots);

        carSlots.clear();
        slots.put(VehicleType.TRUCK, List.of(new ParkingSlot("T-01", VehicleType.TRUCK, 0)));

        assertThat(lot.getSlotsByType(VehicleType.CAR))
                .extracting(ParkingSlot::getSlotId).containsExactly("A-01");
        assertThat(lot.getSlotsByType(VehicleType.TRUCK)).isEmpty();
    }

    @Test
    void slotsCannotBeChangedThroughGetters() {
        ParkingLot lot = lotWith(new HashMap<>(Map.of(VehicleType.CAR,
                new ArrayList<>(List.of(new ParkingSlot("A-01", VehicleType.CAR, 0))))));

        assertThatThrownBy(() -> lot.getSlotsByType(VehicleType.CAR).clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> lot.getSlots().put(VehicleType.TRUCK, List.of()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateSlotId() {
        Map<VehicleType, List<ParkingSlot>> slots = Map.of(
                VehicleType.CAR, List.of(new ParkingSlot("A-01", VehicleType.CAR, 0)),
                VehicleType.MOTORCYCLE, List.of(new ParkingSlot("A-01", VehicleType.MOTORCYCLE, 0)));

        assertThatThrownBy(() -> lotWith(slots))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A-01");
    }

    @Test
    void rejectsSlotListedUnderWrongVehicleType() {
        Map<VehicleType, List<ParkingSlot>> slots = Map.of(
                VehicleType.CAR, List.of(new ParkingSlot("T-01", VehicleType.TRUCK, 0)));

        assertThatThrownBy(() -> lotWith(slots))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("T-01");
    }
}
