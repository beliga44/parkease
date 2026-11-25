package com.parkease.repository;

import com.parkease.domain.RateCard;
import com.parkease.domain.VehicleType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RateCardRepository {

    private final ConcurrentHashMap<VehicleType, RateCard> rateCards = new ConcurrentHashMap<>();

    public RateCard save(RateCard rateCard) {
        rateCards.put(rateCard.getVehicleType(), rateCard);
        return rateCard;
    }

    public Optional<RateCard> findRateCardByVehicleType(VehicleType vehicleType) {
        return Optional.ofNullable(rateCards.get(vehicleType));
    }
}
