package com.parkease.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationStatusTest {

    private static final Set<String> ALLOWED_TRANSITIONS = Set.of(
            "PENDING->ACTIVE",
            "PENDING->CANCELLED",
            "PENDING->NO_SHOW",
            "ACTIVE->COMPLETED");

    static Stream<Arguments> everyPairOfStatuses() {
        return Arrays.stream(ReservationStatus.values())
                .flatMap(from -> Arrays.stream(ReservationStatus.values())
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPairOfStatuses")
    @DisplayName("only the reservation lifecycle transitions are allowed")
    void onlyLifecycleTransitionsAreAllowed(ReservationStatus from, ReservationStatus to) {
        boolean expected = ALLOWED_TRANSITIONS.contains(from + "->" + to);

        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PENDING", "ACTIVE"})
    @DisplayName("PENDING and ACTIVE reservations block their slot's time window")
    void pendingAndActiveBlockTheSlot(ReservationStatus status) {
        assertThat(status.blocksSlot()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ReservationStatus.class, names = {"PENDING", "ACTIVE"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("finished reservations release their slot's time window")
    void finishedReservationsReleaseTheSlot(ReservationStatus status) {
        assertThat(status.blocksSlot()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ReservationStatus.class)
    @DisplayName("only ACTIVE reservations can be extended")
    void onlyActiveCanBeExtended(ReservationStatus status) {
        assertThat(status.canExtend()).isEqualTo(status == ReservationStatus.ACTIVE);
    }
}
