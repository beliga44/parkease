package com.parkease.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ParkingSlotStatusTest {

    @ParameterizedTest
    @EnumSource(value = ParkingSlotStatus.class, names = "MAINTENANCE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("slots in service can be booked for a free time window, even while occupied or reserved")
    void slotsInServiceAreBookable(ParkingSlotStatus status) {
        assertThat(status.isBookable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ParkingSlotStatus.class, names = "MAINTENANCE")
    @DisplayName("slots under maintenance cannot be booked")
    void maintenanceIsNotBookable(ParkingSlotStatus status) {
        assertThat(status.isBookable()).isFalse();
    }
}
