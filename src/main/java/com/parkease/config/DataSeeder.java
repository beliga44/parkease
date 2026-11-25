package com.parkease.config;

import com.parkease.domain.*;
import com.parkease.repository.ParkingLotRepository;
import com.parkease.repository.RateCardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ParkingLotRepository parkingLotRepository;
    private final RateCardRepository rateCardRepository;

    public DataSeeder(
            ParkingLotRepository parkingLotRepository, RateCardRepository rateCardRepository
    ) {
        this.parkingLotRepository = parkingLotRepository;
        this.rateCardRepository = rateCardRepository;
    }

    @Override
    public void run(String... args) {
        seedParkingLots();
        seedRateCards();
    }

    private void seedParkingLots() {
        parkingLotRepository.save(new ParkingLot("LOT-JKT01", "ParkEase Jakarta Central", "Jakarta",
                Map.of(
                        VehicleType.CAR, List.of(
                                new ParkingSlot("A-01", VehicleType.CAR, 0),
                                new ParkingSlot("A-02", VehicleType.CAR, 0),
                                new ParkingSlot("B-01", VehicleType.CAR, 1),
                                new ParkingSlot("B-02", VehicleType.CAR, 1)),
                        VehicleType.MOTORCYCLE, List.of(
                                new ParkingSlot("M-01", VehicleType.MOTORCYCLE, 0),
                                new ParkingSlot("M-02", VehicleType.MOTORCYCLE, 0)),
                        VehicleType.TRUCK, List.of(
                                new ParkingSlot("T-01", VehicleType.TRUCK, 0))),
                OperatingHours.allDay()));

        parkingLotRepository.save(new ParkingLot("LOT-BDG01", "ParkEase Bandung", "Bandung",
                Map.of(
                        VehicleType.CAR, List.of(
                                new ParkingSlot("A-01", VehicleType.CAR, 0))),
                OperatingHours.allDay()));

        log.info("Seeded parking lots LOT-JKT01 and LOT-BDG01");
    }

    private void seedRateCards() {
        rateCardRepository.save(new RateCard(VehicleType.CAR,
                new BigDecimal("5000"), new BigDecimal("50000"), new BigDecimal("20000")));
        rateCardRepository.save(new RateCard(VehicleType.TRUCK,
                new BigDecimal("15000"), new BigDecimal("120000"), new BigDecimal("30000")));
        rateCardRepository.save(new RateCard(VehicleType.MOTORCYCLE,
                new BigDecimal("3000"), new BigDecimal("30000"), new BigDecimal("10000")));

        log.info("Seeded rate cards for CAR, TRUCK and MOTORCYCLE");
    }
}
