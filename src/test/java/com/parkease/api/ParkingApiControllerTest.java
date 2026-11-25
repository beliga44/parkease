package com.parkease.api;

import com.parkease.config.ClockConfig;
import com.parkease.domain.*;
import com.parkease.exception.ReservationConflictException;
import com.parkease.exception.ResourceNotFoundException;
import com.parkease.service.ReservationRequest;
import com.parkease.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReservationController.class, LotController.class, InvoiceController.class})
@Import(ClockConfig.class)
class ParkingApiControllerTest {

    private static final String RESERVATION_ID = "RES-550e8400";
    private static final String LOT_ID = "LOT-JKT01";
    private static final LocalDateTime PLANNED_START = LocalDateTime.parse("2024-03-15T09:00:00");
    private static final LocalDateTime PLANNED_END = LocalDateTime.parse("2024-03-15T13:00:00");

    private static final String VALID_RESERVATION_BODY = """
            {
              "lotId": "LOT-JKT01",
              "vehicleType": "CAR",
              "licensePlate": "B 1234 XYZ",
              "customerId": "CUST-001",
              "plannedStartTime": "2024-03-15T09:00:00",
              "plannedEndTime": "2024-03-15T13:00:00"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @BeforeEach
    void stubSlotLookup() {
        when(reservationService.getSlot(LOT_ID, "A-01")).thenReturn(new ParkingSlot("A-01", VehicleType.CAR, 0));
    }

    private static Reservation pendingReservation() {
        return new Reservation(RESERVATION_ID, LOT_ID, "A-01", VehicleType.CAR,
                "B 1234 XYZ", "CUST-001", PLANNED_START, PLANNED_END);
    }

    private static ParkingInvoice caseAInvoice() {
        return new ParkingInvoice("INV-1", RESERVATION_ID, "CUST-001", 210,
                new BigDecimal("17500.00"), new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("17500.00"), LocalDateTime.parse("2024-03-15T12:45:00"));
    }

    @Nested
    @DisplayName("POST /api/v1/reservations")
    class CreateReservation {

        @Test
        @DisplayName("returns 201 with slot, floor and ISO-8601 times; omits empty fields")
        void returnsCreatedReservation() throws Exception {
            when(reservationService.createReservation(any(ReservationRequest.class))).thenReturn(pendingReservation());

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_RESERVATION_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.reservationId").value(RESERVATION_ID))
                    .andExpect(jsonPath("$.slotId").value("A-01"))
                    .andExpect(jsonPath("$.floorLevel").value(0))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.plannedStartTime").value("2024-03-15T09:00:00"))
                    .andExpect(jsonPath("$.actualStartTime").doesNotExist());
        }

        @Test
        @DisplayName("returns 400 listing each invalid field and never calls the service")
        void rejectsInvalidBody() throws Exception {
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lotId\": \"bad\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'lotId')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'vehicleType')]").exists())
                    .andExpect(jsonPath("$.fieldErrors[?(@.field == 'plannedStartTime')]").exists());

            verify(reservationService, never()).createReservation(any());
        }

        @Test
        @DisplayName("returns 400 naming the field when a date is not ISO-8601")
        void rejectsMalformedDate() throws Exception {
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_RESERVATION_BODY.replace("2024-03-15T09:00:00", "15-03-2024 09:00")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("plannedStartTime"))
                    .andExpect(jsonPath("$.fieldErrors[0].message")
                            .value("must be an ISO-8601 date-time, e.g. 2024-03-15T09:00:00"));
        }

        @Test
        @DisplayName("returns 400 naming the field and allowed values for an unknown enum value")
        void rejectsUnknownEnumInBody() throws Exception {
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_RESERVATION_BODY.replace("\"vehicleType\": \"CAR\"", "\"vehicleType\": \"PLANE\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("vehicleType"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must be one of [MOTORCYCLE, CAR, TRUCK]"));

            verify(reservationService, never()).createReservation(any(ReservationRequest.class));
        }

        @Test
        @DisplayName("returns 400 without field errors when the JSON itself is broken")
        void rejectsBrokenJson() throws Exception {
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lotId\": "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Malformed JSON request body"))
                    .andExpect(jsonPath("$.fieldErrors").isEmpty());
        }

        @ParameterizedTest(name = "plannedEndTime {0}")
        @ValueSource(strings = {"2024-03-15T08:00:00", "2024-03-15T09:00:00"})
        @DisplayName("returns 400 in fieldErrors when plannedEndTime is not after plannedStartTime")
        void rejectsEndNotAfterStart(String plannedEndTime) throws Exception {
            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_RESERVATION_BODY.replace("2024-03-15T13:00:00", plannedEndTime)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("endAfterStart"))
                    .andExpect(jsonPath("$.fieldErrors[0].message")
                            .value("plannedEndTime must be after plannedStartTime"));

            verify(reservationService, never()).createReservation(any(ReservationRequest.class));
        }

        @Test
        @DisplayName("returns 409 with a descriptive message when no slot is available")
        void returnsConflictWhenDoubleBooked() throws Exception {
            when(reservationService.createReservation(any(ReservationRequest.class)))
                    .thenThrow(new ReservationConflictException("No CAR slot available in LOT-JKT01"));

            mockMvc.perform(post("/api/v1/reservations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_RESERVATION_BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409))
                    .andExpect(jsonPath("$.message").value("No CAR slot available in LOT-JKT01"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/reservations/{id}/check-in")
    class CheckIn {

        @Test
        @DisplayName("returns 404 for an unknown reservation")
        void returnsNotFoundForUnknownReservation() throws Exception {
            when(reservationService.checkIn(anyString(), any()))
                    .thenThrow(new ResourceNotFoundException("Reservation RES-unknown not found"));

            mockMvc.perform(post("/api/v1/reservations/RES-unknown/check-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualStartTime\": \"2024-03-15T09:00:00\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Reservation RES-unknown not found"));
        }

        @Test
        @DisplayName("returns 422 when the reservation is in the wrong state")
        void returnsUnprocessableForWrongState() throws Exception {
            when(reservationService.checkIn(anyString(), any()))
                    .thenThrow(new IllegalStateException("Reservation RES-550e8400 cannot go from COMPLETED to ACTIVE"));

            mockMvc.perform(post("/api/v1/reservations/" + RESERVATION_ID + "/check-in")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualStartTime\": \"2024-03-15T09:00:00\"}"))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/reservations/{id}/check-out")
    class CheckOut {

        @Test
        @DisplayName("returns the completed reservation plus its invoice with money as strings")
        void returnsReservationAndInvoice() throws Exception {
            Reservation completed = pendingReservation();
            completed.checkIn(PLANNED_START);
            completed.checkOut(LocalDateTime.parse("2024-03-15T12:45:00"));
            when(reservationService.checkOut(anyString(), any())).thenReturn(completed);
            when(reservationService.getInvoice(RESERVATION_ID)).thenReturn(caseAInvoice());

            mockMvc.perform(post("/api/v1/reservations/" + RESERVATION_ID + "/check-out")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actualEndTime\": \"2024-03-15T12:45:00\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservation.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.reservation.actualEndTime").value("2024-03-15T12:45:00"))
                    .andExpect(jsonPath("$.invoice.billedDurationMinutes").value(210))
                    .andExpect(jsonPath("$.invoice.totalAmount").isString())
                    .andExpect(jsonPath("$.invoice.totalAmount").value("17500.00"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/reservations/{id}")
    class CancelReservation {

        @Test
        @DisplayName("passes the reason to the service and returns the lateCancellation flag")
        void returnsCancelledReservationWithLateFlag() throws Exception {
            Reservation cancelled = pendingReservation();
            cancelled.cancel("change of plans", true);
            when(reservationService.cancelReservation(RESERVATION_ID, "change of plans")).thenReturn(cancelled);

            mockMvc.perform(delete("/api/v1/reservations/" + RESERVATION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"change of plans\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.lateCancellation").value(true));

            verify(reservationService).cancelReservation(RESERVATION_ID, "change of plans");
        }

        @Test
        @DisplayName("returns 400 when the reason is blank")
        void rejectsBlankReason() throws Exception {
            mockMvc.perform(delete("/api/v1/reservations/" + RESERVATION_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("reason"));
        }

        @Test
        @DisplayName("returns 400 'Request body is required' when the body with the reason is missing")
        void rejectsMissingBody() throws Exception {
            mockMvc.perform(delete("/api/v1/reservations/" + RESERVATION_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request body is required"));

            verify(reservationService, never()).cancelReservation(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/reservations/{id}/extend")
    class ExtendReservation {

        @Test
        @DisplayName("returns 409 when the new end time collides with another booking")
        void returnsConflictForBookedWindow() throws Exception {
            when(reservationService.extendReservation(anyString(), any()))
                    .thenThrow(new ReservationConflictException("Slot A-01 is already booked before 2024-03-15T15:00"));

            mockMvc.perform(put("/api/v1/reservations/" + RESERVATION_ID + "/extend")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newEndTime\": \"2024-03-15T15:00:00\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/lots/{lotId}/availability")
    class Availability {

        @Test
        @DisplayName("returns available slots with slotId and floorLevel in service order")
        void returnsAvailableSlots() throws Exception {
            when(reservationService.findAvailableSlots(LOT_ID, VehicleType.CAR, PLANNED_START, PLANNED_END))
                    .thenReturn(List.of(new ParkingSlot("A-01", VehicleType.CAR, 0),
                            new ParkingSlot("B-01", VehicleType.CAR, 1)));

            mockMvc.perform(get("/api/v1/lots/LOT-JKT01/availability")
                            .param("vehicleType", "CAR")
                            .param("startTime", "2024-03-15T09:00:00")
                            .param("endTime", "2024-03-15T13:00:00"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].slotId").value("A-01"))
                    .andExpect(jsonPath("$[0].floorLevel").value(0))
                    .andExpect(jsonPath("$[1].slotId").value("B-01"));
        }

        @Test
        @DisplayName("returns 400 when a required query parameter is missing")
        void rejectsMissingParameter() throws Exception {
            mockMvc.perform(get("/api/v1/lots/LOT-JKT01/availability")
                            .param("vehicleType", "CAR"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 naming the query parameter for an unknown vehicle type")
        void rejectsUnknownVehicleType() throws Exception {
            mockMvc.perform(get("/api/v1/lots/LOT-JKT01/availability")
                            .param("vehicleType", "BUS")
                            .param("startTime", "2024-03-15T09:00:00")
                            .param("endTime", "2024-03-15T13:00:00"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("vehicleType"))
                    .andExpect(jsonPath("$.fieldErrors[0].message").value("must be one of [MOTORCYCLE, CAR, TRUCK]"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/invoices/{reservationId}")
    class GetInvoice {

        @Test
        @DisplayName("returns the invoice with every money field as a string")
        void returnsInvoice() throws Exception {
            when(reservationService.getInvoice(RESERVATION_ID)).thenReturn(caseAInvoice());

            mockMvc.perform(get("/api/v1/invoices/" + RESERVATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.baseAmount").value("17500.00"))
                    .andExpect(jsonPath("$.overnightSurchargeApplied").value("0.00"))
                    .andExpect(jsonPath("$.discountAmount").value("0.00"))
                    .andExpect(jsonPath("$.totalAmount").value("17500.00"))
                    .andExpect(jsonPath("$.generatedAt").value("2024-03-15T12:45:00"));
        }

        @Test
        @DisplayName("returns 404 when no invoice exists for the reservation")
        void returnsNotFound() throws Exception {
            when(reservationService.getInvoice("RES-unknown"))
                    .thenThrow(new ResourceNotFoundException("Invoice for reservation RES-unknown not found"));

            mockMvc.perform(get("/api/v1/invoices/RES-unknown"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    @DisplayName("returns 405 for an unsupported HTTP method")
    void returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isMethodNotAllowed());
    }
}
