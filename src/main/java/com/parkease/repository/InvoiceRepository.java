package com.parkease.repository;

import com.parkease.domain.ParkingInvoice;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InvoiceRepository {

    private final ConcurrentHashMap<String, ParkingInvoice> invoices = new ConcurrentHashMap<>();

    public ParkingInvoice save(ParkingInvoice invoice) {
        invoices.put(invoice.getReservationId(), invoice);
        return invoice;
    }

    public Optional<ParkingInvoice> findInvoiceByReservationId(String id) {
        return Optional.ofNullable(invoices.get(id));
    }
}
