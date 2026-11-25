package com.parkease.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static com.parkease.domain.Guards.requireNonNull;
import static com.parkease.domain.Guards.requireNotBlank;

public class ParkingLot {
    private static final Pattern LOT_ID_PATTERN = Pattern.compile("LOT-[A-Z0-9]{3,8}");

    private final String lotId;
    private final String name;
    private final String location;
    private final Map<VehicleType, List<ParkingSlot>> slots;
    private final OperatingHours operatingHours;

    public ParkingLot(
            String lotId,
            String name,
            String location,
            Map<VehicleType, List<ParkingSlot>> slots,
            OperatingHours operatingHours
    ) {
        if (lotId == null || !LOT_ID_PATTERN.matcher(lotId).matches()) {
            throw new IllegalArgumentException("lotId must match pattern LOT-[A-Z0-9]{3,8}");
        }
        this.lotId = lotId;
        this.name = requireNotBlank(name, "name");
        this.location = requireNotBlank(location, "location");
        this.slots = validatedCopy(slots);
        this.operatingHours = requireNonNull(operatingHours, "operatingHours");
    }

    public String getLotId() { return lotId; }
    public String getName() { return name; }
    public String getLocation() { return location; }
    public Map<VehicleType, List<ParkingSlot>> getSlots() { return slots; }
    public OperatingHours getOperatingHours() { return operatingHours; }

    public Optional<ParkingSlot> findSlot(String slotId) {
        return slots.values().stream()
                .flatMap(List::stream)
                .filter(s -> s.getSlotId().equals(slotId))
                .findFirst();
    }

    public List<ParkingSlot> getSlotsByType(VehicleType vehicleType) {
        return slots.getOrDefault(vehicleType, List.of());
    }

    private static Map<VehicleType, List<ParkingSlot>> validatedCopy(Map<VehicleType, List<ParkingSlot>> slots) {
        requireNonNull(slots, "slots");
        Map<VehicleType, List<ParkingSlot>> copy = new EnumMap<>(VehicleType.class);
        Set<String> seenSlotIds = new HashSet<>();

        slots.forEach((vehicleType, slotList) -> {
            List<ParkingSlot> copied = List.copyOf(requireNonNull(slotList, "slots for " + vehicleType));
            for (ParkingSlot slot : copied) {
                if (slot.getVehicleType() != vehicleType) {
                    throw new IllegalArgumentException("Slot " + slot.getSlotId() + " is not a " + vehicleType + " slot");
                }
                if (!seenSlotIds.add(slot.getSlotId())) {
                    throw new IllegalArgumentException("Duplicate slotId in lot: " + slot.getSlotId());
                }
            }
            copy.put(vehicleType, copied);
        });
        return Collections.unmodifiableMap(copy);
    }
}
