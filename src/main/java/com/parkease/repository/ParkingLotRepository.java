package com.parkease.repository;

import com.parkease.domain.ParkingLot;
import com.parkease.domain.ParkingSlot;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ParkingLotRepository {

    private final ConcurrentHashMap<String, ParkingLot> lots = new ConcurrentHashMap<>();

    public ParkingLot save(ParkingLot lot) {
        lots.put(lot.getLotId(), lot);
        return lot;
    }

    public Optional<ParkingLot> findParkingLotById(String id) {
        return Optional.ofNullable(lots.get(id));
    }

}
