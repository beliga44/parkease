package com.parkease.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against the full application with the seeded data from DataSeeder.
 * Each test books a different date, so tests sharing the same in-memory context never collide.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ParkingApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private String createReservation(String vehicleType, String start, String end) throws Exception {
        String body = """
                {
                  "lotId": "LOT-JKT01",
                  "vehicleType": "%s",
                  "licensePlate": "B 1234 XYZ",
                  "customerId": "CUST-001",
                  "plannedStartTime": "%s",
                  "plannedEndTime": "%s"
                }
                """.formatted(vehicleType, start, end);

        String response = mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.reservationId");
    }

    @Test
    @DisplayName("create -> check-in -> check-out -> get invoice produces the Case A invoice")
    void fullReservationLifecycle() throws Exception {
        String reservationId = createReservation("CAR", "2030-01-10T09:00:00", "2030-01-10T13:00:00");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/check-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualStartTime\": \"2030-01-10T09:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/check-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualEndTime\": \"2030-01-10T12:45:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservation.status").value("COMPLETED"))
                .andExpect(jsonPath("$.invoice.totalAmount").value("17500.00"));

        mockMvc.perform(get("/api/v1/invoices/" + reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billedDurationMinutes").value(210))
                .andExpect(jsonPath("$.totalAmount").value("17500.00"));
    }

    @Test
    @DisplayName("booking the only truck slot twice for an overlapping window returns 409")
    void doubleBookingReturnsConflict() throws Exception {
        createReservation("TRUCK", "2030-02-10T09:00:00", "2030-02-10T11:00:00");

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lotId": "LOT-JKT01",
                                  "vehicleType": "TRUCK",
                                  "licensePlate": "B 9 T",
                                  "customerId": "CUST-002",
                                  "plannedStartTime": "2030-02-10T10:00:00",
                                  "plannedEndTime": "2030-02-10T12:00:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
