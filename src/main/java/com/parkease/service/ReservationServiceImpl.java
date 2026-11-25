package com.parkease.service;

import com.parkease.domain.*;
import com.parkease.exception.ReservationConflictException;
import com.parkease.exception.ResourceNotFoundException;
import com.parkease.repository.InvoiceRepository;
import com.parkease.repository.ParkingLotRepository;
import com.parkease.repository.RateCardRepository;
import com.parkease.repository.ReservationRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
public class ReservationServiceImpl implements ReservationService {
    private static final Duration CHECK_IN_WINDOW = Duration.ofMinutes(30);
    private static final Duration LATE_CANCEL_WINDOW = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, ReentrantLock> lotLocks = new ConcurrentHashMap<>();

    private final ParkingLotRepository parkingLotRepository;
    private final ReservationRepository reservationRepository;
    private final RateCardRepository rateCardRepository;
    private final InvoiceRepository invoiceRepository;

    private final BillingService billingService;

    private final Clock clock;

    public ReservationServiceImpl(
            ParkingLotRepository parkingLotRepository,
            ReservationRepository reservationRepository,
            RateCardRepository rateCardRepository,
            InvoiceRepository invoiceRepository,
            BillingService billingService,
            Clock clock
    ) {
        this.parkingLotRepository = parkingLotRepository;
        this.reservationRepository = reservationRepository;
        this.rateCardRepository = rateCardRepository;
        this.invoiceRepository = invoiceRepository;

        this.billingService = billingService;

        this.clock = clock;
    }

    private Reservation getReservationById(String id) {
        return reservationRepository
                .findReservationById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation " + id + " not " +
                        "found"));
    }

    private ParkingLot getParkingLotById(String id) {
        return parkingLotRepository
                .findParkingLotById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ParkingLot " + id + " not " +
                        "found"));
    }

    private ParkingSlot getSlot(Reservation reservation) {
        return getSlot(reservation.getLotId(), reservation.getSlotId());
    }

    private RateCard getRateCardByVehicleType(VehicleType vehicleType) {
        return rateCardRepository.findRateCardByVehicleType(vehicleType)
                .orElseThrow(() -> new ResourceNotFoundException("RateCard for vehicle " + vehicleType.toString() + " not found"));
    }

    @Override
    public List<ParkingSlot> findAvailableSlots(String lotId, VehicleType vehicleType,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }

        ParkingLot lot = getParkingLotById(lotId);
        return withLock(lotId, () -> lot.getSlotsByType(vehicleType).stream()
                .filter(ParkingSlot::isBookable)
                .filter(slot -> reservationRepository.hasNoConflictReservation(
                        lotId, slot.getSlotId(), null, startTime, endTime
                ))
                .sorted(
                        Comparator.comparingInt(ParkingSlot::getFloorLevel)
                                .thenComparing(ParkingSlot::getSlotId)
                )
                .toList());
    }

    @Override
    public ParkingSlot getSlot(String lotId, String slotId) {
        ParkingLot lot = getParkingLotById(lotId);

        return lot.findSlot(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("ParkingSlot " +
                        slotId + " not found"));
    }

    @Override
    public ParkingInvoice getInvoice(String reservationId) {
        return invoiceRepository.findInvoiceByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice for id " + reservationId + " not " +
                        "found"));
    }

    @Override
    public Optional<ParkingSlot> findAvailableSlot(String lotId, VehicleType vehicleType,
                                         LocalDateTime startTime, LocalDateTime endTime) {
        return findAvailableSlots(lotId, vehicleType, startTime, endTime).stream()
                .findFirst();
    }

    @Override
    public Reservation createReservation(ReservationRequest request) {
        if (request.plannedStartTime() == null
                || request.plannedEndTime() == null
                || !request.plannedEndTime().isAfter(request.plannedStartTime())) {
            throw new IllegalArgumentException("plannedEndTime must be after plannedStartTime");
        }

        getParkingLotById(request.lotId());
        return withLock(request.lotId(), () -> {
            ParkingSlot parkingSlot = findAvailableSlot(request.lotId(),
                    request.vehicleType(),
                    request.plannedStartTime(), request.plannedEndTime())
                    .orElseThrow(() -> new ReservationConflictException("No " + request.vehicleType()
                            + " slot available in " + request.lotId() + " from " + request.plannedStartTime()
                            + " to " + request.plannedEndTime()));

            Reservation reservation = new Reservation(
                    "RES-" + UUID.randomUUID(),
                    request.lotId(),
                    parkingSlot.getSlotId(),
                    request.vehicleType(),
                    request.licensePlate(),
                    request.customerId(),
                    request.plannedStartTime(),
                    request.plannedEndTime()
            );

            reservationRepository.save(reservation);

            // Change status only when slot is available, unless it will overwrite current status
            // of slot
            if (parkingSlot.getStatus() == ParkingSlotStatus.AVAILABLE) {
                parkingSlot.markReserved();
            }

            return reservation;
        });
    }

    @Override
    public Reservation checkIn(String reservationId, LocalDateTime actualStartTime) {
        if (actualStartTime == null) {
            throw new IllegalArgumentException("actualStartTime is required");
        }

        Reservation reservation = getReservationById(reservationId);
        return withLock(reservation.getLotId(), () -> {
            ParkingSlot parkingSlot = getSlot(reservation);

            if (actualStartTime.isBefore(reservation.getPlannedStartTime().minus(CHECK_IN_WINDOW))) {
                throw new IllegalStateException("Check-in opens 30 minutes before plannedStartTime");
            }

            if (actualStartTime.isAfter(reservation.getPlannedStartTime().plus(CHECK_IN_WINDOW))) {
                reservation.markNoShow();
                parkingSlot.markAvailable();
            } else {
                reservation.checkIn(actualStartTime);
                parkingSlot.markOccupied();
            }

            return reservation;
        });
    }

    @Override
    public Reservation checkOut(String reservationId, LocalDateTime actualEndTime) {
        if (actualEndTime == null) {
            throw new IllegalArgumentException("actualEndTime is required");
        }

        Reservation reservation = getReservationById(reservationId);
        return withLock(reservation.getLotId(), () -> {
            ParkingSlot parkingSlot = getSlot(reservation);
            RateCard rateCard = getRateCardByVehicleType(reservation.getVehicleType());

            reservation.checkOut(actualEndTime);
            parkingSlot.markAvailable();
            invoiceRepository.save(billingService.generateInvoice(
                    reservation,
                    rateCard
            ));

            return reservation;
        });
    }

    @Override
    public Reservation cancelReservation(String reservationId, String reason) {
        Reservation reservation = getReservationById(reservationId);
        return withLock(reservation.getLotId(), () -> {
            ParkingSlot parkingSlot = getSlot(reservation);
            LocalDateTime lateThreshold =
                    reservation.getPlannedStartTime().minus(LATE_CANCEL_WINDOW);
            boolean late = !LocalDateTime.now(clock).isBefore(lateThreshold);

            reservation.cancel(reason, late);
            parkingSlot.markAvailable();

            return reservation;
        });
    }

    @Override
    public Reservation extendReservation(String reservationId, LocalDateTime newEndTime) {
        Reservation reservation = getReservationById(reservationId);
        return withLock(reservation.getLotId(), () -> {
            if (newEndTime == null || !newEndTime.isAfter(reservation.getPlannedEndTime())) {
                throw new IllegalArgumentException("newEndTime must be after current plannedEndTime");
            }

            if (!reservationRepository.hasNoConflictReservation(reservation.getLotId(),
                    reservation.getSlotId(), reservationId, reservation.getPlannedEndTime(),
                    newEndTime)) {
                throw new ReservationConflictException("Slot " + reservation.getSlotId()
                        + " is already booked before " + newEndTime);
            }

            reservation.extend(newEndTime);
            return reservation;
        });
    }

    // Package-private so tests can check that unknown lot IDs never create a lock.
    boolean hasLockFor(String lotId) {
        return lotLocks.containsKey(lotId);
    }

    private <T> T withLock(String lotId, Supplier<T> action) {
        ReentrantLock lock = lotLocks.computeIfAbsent(lotId, id -> new ReentrantLock());
        lock.lock();

        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
