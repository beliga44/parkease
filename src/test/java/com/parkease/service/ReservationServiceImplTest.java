package com.parkease.service;

import com.parkease.domain.*;
import com.parkease.exception.ReservationConflictException;
import com.parkease.exception.ResourceNotFoundException;
import com.parkease.repository.InvoiceRepository;
import com.parkease.repository.ParkingLotRepository;
import com.parkease.repository.RateCardRepository;
import com.parkease.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationServiceImplTest {

    private static final String LOT_ID = "LOT-JKT01";
    private static final LocalDateTime NOW = LocalDateTime.parse("2024-03-15T07:00:00");

    private ParkingSlot carA01;
    private ParkingSlot carA02;
    private ParkingSlot carB01;
    private ParkingSlot truckT01;

    private ReservationRepository reservationRepository;
    private InvoiceRepository invoiceRepository;
    private ReservationServiceImpl reservationService;

    @BeforeEach
    void setUp() {
        carA01 = new ParkingSlot("A-01", VehicleType.CAR, 1);
        carA02 = new ParkingSlot("A-02", VehicleType.CAR, 0);
        carB01 = new ParkingSlot("B-01", VehicleType.CAR, 0);
        truckT01 = new ParkingSlot("T-01", VehicleType.TRUCK, 0);

        ParkingLotRepository parkingLotRepository = new ParkingLotRepository();
        parkingLotRepository.save(new ParkingLot(LOT_ID, "Jakarta Central", "Jakarta",
                Map.of(VehicleType.CAR, List.of(carA01, carB01, carA02),
                        VehicleType.TRUCK, List.of(truckT01)),
                OperatingHours.allDay()));

        RateCardRepository rateCardRepository = new RateCardRepository();
        rateCardRepository.save(new RateCard(VehicleType.CAR,
                new BigDecimal("5000"), new BigDecimal("50000"), new BigDecimal("20000")));
        rateCardRepository.save(new RateCard(VehicleType.TRUCK,
                new BigDecimal("15000"), new BigDecimal("120000"), new BigDecimal("30000")));

        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        reservationRepository = new ReservationRepository();
        invoiceRepository = new InvoiceRepository();
        reservationService = new ReservationServiceImpl(parkingLotRepository, reservationRepository,
                rateCardRepository, invoiceRepository, new BillingServiceImpl(clock), clock);
    }

    private Reservation book(VehicleType vehicleType, String customerId, String start, String end) {
        return reservationService.createReservation(new ReservationRequest(LOT_ID, vehicleType,
                "B 1234 XYZ", customerId, LocalDateTime.parse(start), LocalDateTime.parse(end)));
    }

    @Nested
    @DisplayName("createReservation")
    class CreateReservation {

        @Test
        @DisplayName("creates a PENDING reservation and marks the slot RESERVED")
        void marksSlotReserved() {
            Reservation reservation = book(VehicleType.CAR, "CUST-001", "2024-03-15T09:00", "2024-03-15T13:00");

            assertThat(reservation.getReservationId()).startsWith("RES-");
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(reservation.getSlotId()).isEqualTo("A-02");
            assertThat(carA02.getStatus()).isEqualTo(ParkingSlotStatus.RESERVED);
        }

        @Test
        @DisplayName("prefers ground floor, then lowest slotId alphabetically")
        void selectsSlotsByFloorThenSlotId() {
            String first = book(VehicleType.CAR, "CUST-001", "2024-03-15T09:00", "2024-03-15T13:00").getSlotId();
            String second = book(VehicleType.CAR, "CUST-002", "2024-03-15T09:00", "2024-03-15T13:00").getSlotId();
            String third = book(VehicleType.CAR, "CUST-003", "2024-03-15T09:00", "2024-03-15T13:00").getSlotId();

            assertThat(List.of(first, second, third)).containsExactly("A-02", "B-01", "A-01");
        }

        @Test
        @DisplayName("double-booking the only slot for an overlapping window throws a conflict")
        void doubleBookingThrowsConflict() {
            book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");

            assertThatThrownBy(() -> book(VehicleType.TRUCK, "CUST-002", "2024-03-15T10:00", "2024-03-15T12:00"))
                    .isInstanceOf(ReservationConflictException.class);
        }

        @Test
        @DisplayName("an unknown lot returns not found and leaves no lock behind")
        void unknownLotCreatesNoLock() {
            ReservationRequest request = new ReservationRequest("LOT-FAKE1", VehicleType.CAR, "B 1234 XYZ",
                    "CUST-001", LocalDateTime.parse("2024-03-15T09:00"), LocalDateTime.parse("2024-03-15T13:00"));

            assertThatThrownBy(() -> reservationService.createReservation(request))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThat(reservationService.hasLockFor("LOT-FAKE1")).isFalse();
        }

        @Test
        @DisplayName("skips a slot under MAINTENANCE and books the next one in preference order")
        void skipsSlotUnderMaintenance() {
            carA02.markMaintenance();

            Reservation reservation = book(VehicleType.CAR, "CUST-001", "2024-03-15T09:00", "2024-03-15T13:00");

            assertThat(reservation.getSlotId()).isEqualTo("B-01");
        }

        @Test
        @DisplayName("50 concurrent bookings for the only slot: exactly 1 succeeds, 49 get a conflict")
        void concurrentBookingsNeverDoubleBook() throws Exception {
            int threads = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch startGate = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger conflicts = new AtomicInteger();

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String customerId = "CUST-" + i;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        book(VehicleType.TRUCK, customerId, "2024-03-15T09:00", "2024-03-15T11:00");
                        successes.incrementAndGet();
                    } catch (ReservationConflictException e) {
                        conflicts.incrementAndGet();
                    }
                    return null;
                }));
            }

            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            executor.shutdown();

            assertThat(successes.get()).isEqualTo(1);
            assertThat(conflicts.get()).isEqualTo(threads - 1);
            assertThat(reservationRepository.findReservationBySlot(LOT_ID, "T-01")).hasSize(1);
        }
    }

    @Nested
    @DisplayName("checkIn")
    class CheckIn {

        @Test
        @DisplayName("checking in more than 30 minutes late marks NO_SHOW and frees the slot")
        void lateCheckInBecomesNoShow() {
            Reservation reservation = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");

            reservationService.checkIn(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T09:31"));

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
            assertThat(truckT01.getStatus()).isEqualTo(ParkingSlotStatus.AVAILABLE);
        }

        @Test
        @DisplayName("checking in within the window activates the reservation and occupies the slot")
        void onTimeCheckInActivates() {
            Reservation reservation = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");

            reservationService.checkIn(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T08:43"));

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
            assertThat(reservation.getActualStartTime()).isEqualTo(LocalDateTime.parse("2024-03-15T08:43"));
            assertThat(truckT01.getStatus()).isEqualTo(ParkingSlotStatus.OCCUPIED);
        }

        @Test
        @DisplayName("checking in exactly 30 minutes late is still within the window")
        void checkInAtExactly30MinutesLateActivates() {
            Reservation reservation = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");

            reservationService.checkIn(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T09:30"));

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
            assertThat(truckT01.getStatus()).isEqualTo(ParkingSlotStatus.OCCUPIED);
        }

        @Test
        @DisplayName("checking in more than 30 minutes early is rejected and changes nothing")
        void earlyCheckInIsRejected() {
            Reservation reservation = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");

            assertThatThrownBy(() -> reservationService.checkIn(
                    reservation.getReservationId(), LocalDateTime.parse("2024-03-15T08:29")))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(reservation.getActualStartTime()).isNull();
            assertThat(truckT01.getStatus()).isEqualTo(ParkingSlotStatus.RESERVED);
        }

        @Test
        @DisplayName("a NO_SHOW releases its window, so another customer can book the same slot")
        void noShowWindowCanBeRebooked() {
            Reservation noShow = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");
            reservationService.checkIn(noShow.getReservationId(), LocalDateTime.parse("2024-03-15T09:31"));

            Reservation rebooked = book(VehicleType.TRUCK, "CUST-002", "2024-03-15T09:00", "2024-03-15T11:00");

            assertThat(rebooked.getSlotId()).isEqualTo("T-01");
            assertThat(rebooked.getStatus()).isEqualTo(ReservationStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("cancelReservation")
    class CancelReservation {

        @Test
        @DisplayName("cancelling a PENDING reservation returns the slot to AVAILABLE")
        void cancelPendingFreesSlot() {
            Reservation reservation = book(VehicleType.CAR, "CUST-001", "2024-03-15T09:00", "2024-03-15T13:00");

            reservationService.cancelReservation(reservation.getReservationId(), "change of plans");

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
            assertThat(reservation.getCancellationReason()).isEqualTo("change of plans");
            assertThat(reservation.getLateCancellation()).isFalse();
            assertThat(carA02.getStatus()).isEqualTo(ParkingSlotStatus.AVAILABLE);
        }

        @Test
        @DisplayName("cancelling within 30 minutes of plannedStartTime sets the late-cancellation flag")
        void cancelWithin30MinutesIsLate() {
            Reservation reservation = book(VehicleType.CAR, "CUST-001", "2024-03-15T07:20", "2024-03-15T09:00");

            reservationService.cancelReservation(reservation.getReservationId(), "running late");

            assertThat(reservation.getLateCancellation()).isTrue();
        }

        @Test
        @DisplayName("a cancelled reservation releases its window, so another customer can book the same slot")
        void cancelledWindowCanBeRebooked() {
            Reservation cancelled = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");
            reservationService.cancelReservation(cancelled.getReservationId(), "change of plans");

            Reservation rebooked = book(VehicleType.TRUCK, "CUST-002", "2024-03-15T09:00", "2024-03-15T11:00");

            assertThat(rebooked.getSlotId()).isEqualTo("T-01");
            assertThat(rebooked.getStatus()).isEqualTo(ReservationStatus.PENDING);
        }

        @Test
        @DisplayName("cancelling an ACTIVE reservation is rejected: the parked vehicle must check out and be billed")
        void cancelActiveIsRejected() {
            Reservation reservation = book(VehicleType.CAR, "CUST-001", "2024-03-15T09:00", "2024-03-15T13:00");
            reservationService.checkIn(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T09:00"));

            assertThatThrownBy(() -> reservationService.cancelReservation(reservation.getReservationId(), "leaving"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("check out instead");
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
            assertThat(reservation.getCancellationReason()).isNull();
            assertThat(carA02.getStatus()).isEqualTo(ParkingSlotStatus.OCCUPIED);
        }

        @Test
        @DisplayName("cancelling a COMPLETED reservation is rejected and leaves it COMPLETED")
        void cancelCompletedIsRejected() {
            Reservation reservation = book(VehicleType.CAR, "CUST-001", "2024-03-15T09:00", "2024-03-15T13:00");
            reservationService.checkIn(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T09:00"));
            reservationService.checkOut(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T12:45"));

            assertThatThrownBy(() -> reservationService.cancelReservation(reservation.getReservationId(), "too late"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COMPLETED to CANCELLED");
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
            assertThat(reservation.getCancellationReason()).isNull();
        }
    }

    @Nested
    @DisplayName("extendReservation")
    class ExtendReservation {

        @Test
        @DisplayName("extending into a window booked by another customer throws a conflict")
        void extendIntoBookedWindowThrowsConflict() {
            Reservation first = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");
            book(VehicleType.TRUCK, "CUST-002", "2024-03-15T11:00", "2024-03-15T13:00");
            reservationService.checkIn(first.getReservationId(), LocalDateTime.parse("2024-03-15T09:00"));

            assertThatThrownBy(() -> reservationService.extendReservation(
                    first.getReservationId(), LocalDateTime.parse("2024-03-15T12:00")))
                    .isInstanceOf(ReservationConflictException.class);
            assertThat(first.getPlannedEndTime()).isEqualTo(LocalDateTime.parse("2024-03-15T11:00"));
        }

        @Test
        @DisplayName("extending into a free window updates plannedEndTime")
        void extendIntoFreeWindowSucceeds() {
            Reservation reservation = book(VehicleType.TRUCK, "CUST-001", "2024-03-15T09:00", "2024-03-15T11:00");
            reservationService.checkIn(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T09:00"));

            reservationService.extendReservation(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T15:00"));

            assertThat(reservation.getPlannedEndTime()).isEqualTo(LocalDateTime.parse("2024-03-15T15:00"));
        }
    }

    @Nested
    @DisplayName("checkOut")
    class CheckOut {

        @Test
        @DisplayName("checking out completes the reservation, frees the slot and stores the invoice")
        void checkOutTriggersBilling() {
            Reservation reservation = book(VehicleType.CAR, "CUST-001", "2024-03-15T09:00", "2024-03-15T13:00");
            reservationService.checkIn(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T09:00"));

            reservationService.checkOut(reservation.getReservationId(), LocalDateTime.parse("2024-03-15T12:45"));

            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
            assertThat(carA02.getStatus()).isEqualTo(ParkingSlotStatus.AVAILABLE);
            assertThat(invoiceRepository.findInvoiceByReservationId(reservation.getReservationId()))
                    .get()
                    .extracting(ParkingInvoice::getTotalAmount)
                    .isEqualTo(new BigDecimal("17500.00"));
        }
    }
}
