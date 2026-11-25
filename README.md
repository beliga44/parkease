# ParkEase Backend

Parking lot reservation and billing service: slot availability, the reservation lifecycle (create, check-in, check-out, cancel, extend) and invoice calculation with grace periods, daily caps and overnight surcharges.

## Tech Stack

- Java 17+
- Spring Boot 3.5 (Web, Validation)
- Maven (wrapper included, no local Maven install needed)
- JUnit 5, AssertJ, Mockito, MockMvc
- In-memory storage (`ConcurrentHashMap`), seeded at startup

## Build and Run

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

To build a jar instead:

```bash
./mvnw clean package
java -jar target/parkease-backend-0.0.1-SNAPSHOT.jar
```

## Run Tests

```bash
./mvnw test
```

Results are printed to the console. Per-class reports are written to `target/surefire-reports/`. Parameterized tests count each case separately, so the total is 96.

| Test class | Type | Tests | Covers |
|---|---|---|---|
| `BillingServiceImplTest` | Unit | 10 | Reference cases A, B, C; exact 30-minute boundary; partial block rounding; 3-day stay with two surcharges; stay equal to the grace period (Rp 0); 1 minute past grace (1 block); daily cap on a single-day stay; leaving at exactly 00:00 (no surcharge) vs 00:01 |
| `ReservationServiceImplTest` | Unit | 19 | Slot selection order; `MAINTENANCE` slot skipped; create (slot `RESERVED`); double-booking; unknown lot returns not found without creating a lock; 50-thread concurrent booking; check-in window boundaries (exactly 30 minutes late accepted, more than 30 minutes early rejected); late check-in (`NO_SHOW`); cancel; late-cancellation flag; cancelling an `ACTIVE` or `COMPLETED` reservation rejected; cancelled and `NO_SHOW` windows can be rebooked; extend conflict; check-out with invoice |
| `ReservationStatusTest` | Unit | 35 | Every one of the 25 status-to-status transitions checked against the allowed lifecycle; which statuses block a slot; only `ACTIVE` can be extended |
| `ParkingSlotStatusTest` | Unit | 4 | Only `MAINTENANCE` slots are not bookable |
| `ParkingLotTest` | Unit | 4 | Slot collections are copied on construction and can't be changed from outside; duplicate slot IDs within a lot rejected; slot listed under the wrong vehicle type rejected |
| `ParkingApiControllerTest` | `@WebMvcTest` slice | 21 | Status codes (201/400/404/405/409/422), validation error body, `plannedEndTime` before or equal to `plannedStartTime` reported in `fieldErrors`, unreadable values (bad date, unknown enum) named in `fieldErrors` for body and query parameters, broken JSON, missing request body, ISO-8601 dates, money serialized as strings |
| `ParkingApiIntegrationTest` | `@SpringBootTest` | 2 | Full HTTP flow create → check-in → check-out → invoice (Case A); double-booking returns 409 |
| `ParkeaseBackendApplicationTests` | Context | 1 | Application context starts |

## Seed Data

Loaded at startup by `config/DataSeeder`. All lots are open 24 hours.

**LOT-JKT01** (ParkEase Jakarta Central)

| Vehicle type | Slot | Floor |
|---|---|---|
| CAR | A-01, A-02 | 0 |
| CAR | B-01, B-02 | 1 |
| MOTORCYCLE | M-01, M-02 | 0 |
| TRUCK | T-01 | 0 |

**LOT-BDG01** (ParkEase Bandung): CAR slot A-01, floor 0. It shares the slot ID `A-01` with LOT-JKT01 on purpose, to show that slot IDs are unique only within a lot.

**Rate cards** (grace period 15 minutes for all)

| Vehicle type | Hourly rate | Daily cap | Overnight surcharge |
|---|---|---|---|
| CAR | Rp 5,000 | Rp 50,000 | Rp 20,000 |
| TRUCK | Rp 15,000 | Rp 120,000 | Rp 30,000 |
| MOTORCYCLE | Rp 3,000 | Rp 30,000 * | Rp 10,000 * |

\* Not given in the spec; assumed values.

## API

| Method | Path | Body | Success |
|---|---|---|---|
| GET | `/api/v1/lots/{lotId}/availability?vehicleType=&startTime=&endTime=` | none | 200, list of `{slotId, floorLevel, vehicleType}` |
| POST | `/api/v1/reservations` | `ReservationRequest` | 201, reservation |
| POST | `/api/v1/reservations/{id}/check-in` | `{"actualStartTime": "..."}` | 200, reservation |
| POST | `/api/v1/reservations/{id}/check-out` | `{"actualEndTime": "..."}` | 200, `{reservation, invoice}` |
| DELETE | `/api/v1/reservations/{id}` | `{"reason": "..."}` (required) | 200, cancelled reservation with `lateCancellation`. `PENDING` reservations only |
| PUT | `/api/v1/reservations/{id}/extend` | `{"newEndTime": "..."}` | 200, reservation |
| GET | `/api/v1/invoices/{reservationId}` | none | 200, invoice |

All date-times are ISO-8601 (`2024-03-15T09:00:00`). All money fields in responses are strings (`"17500.00"`).

### Example: Case A through the API

```bash
# Create
curl -X POST localhost:8080/api/v1/reservations -H "Content-Type: application/json" -d '{
  "lotId": "LOT-JKT01", "vehicleType": "CAR", "licensePlate": "B 1234 XYZ", "customerId": "CUST-001",
  "plannedStartTime": "2024-03-15T09:00:00", "plannedEndTime": "2024-03-15T13:00:00"}'
# -> 201 {"reservationId":"RES-...","slotId":"A-01","floorLevel":0,"status":"PENDING",...}

# Check in and check out (replace RES-... with the returned id)
curl -X POST localhost:8080/api/v1/reservations/RES-.../check-in \
  -H "Content-Type: application/json" -d '{"actualStartTime": "2024-03-15T09:00:00"}'
curl -X POST localhost:8080/api/v1/reservations/RES-.../check-out \
  -H "Content-Type: application/json" -d '{"actualEndTime": "2024-03-15T12:45:00"}'
# -> 200 {"reservation":{...,"status":"COMPLETED"},
#         "invoice":{"billedDurationMinutes":210,"baseAmount":"17500.00","totalAmount":"17500.00",...}}

# Get the invoice
curl localhost:8080/api/v1/invoices/RES-...
```

### Errors

Every error returns the same body:

```json
{
  "status": 409,
  "error": "Conflict",
  "message": "No TRUCK slot available in LOT-JKT01 from 2024-03-15T09:00 to 2024-03-15T11:00",
  "fieldErrors": [],
  "timestamp": "2024-03-15T07:00:00"
}
```

| Status | When |
|---|---|
| 400 | Invalid or malformed input: Bean Validation failures (including `plannedEndTime` not after `plannedStartTime`, reported as `endAfterStart`) and unreadable values such as non-ISO dates or unknown enum values, all listed in `fieldErrors` with the field name and the expected format; missing request body; broken JSON; missing query parameters; availability `endTime` not after `startTime` |
| 404 | Unknown reservation, lot, slot, rate card or invoice; unknown URL |
| 405 | HTTP method not supported for the path |
| 409 | Double-booking, no slot available, extension into a booked window |
| 422 | Valid request that breaks a business rule: wrong reservation status (for example, checking out a `PENDING` reservation or cancelling an `ACTIVE` one) or check-in more than 30 minutes early |
| 500 | Unexpected error. The client gets a generic message; details are logged on the server only. |

## Billing Rules (as implemented)

1. Grace period is deducted once from the start of the stay. If the stay is within the grace period, the total is Rp 0.00 and no surcharge applies.
2. The remaining time is split into calendar days (00:00 boundaries). Each day's minutes are rounded up to full 30-minute blocks.
3. Each block costs half the hourly rate. Each day's charge is capped at `dailyCap`.
4. `overnightSurcharge` is applied once per midnight crossed during the actual stay.
5. `totalAmount = baseAmount + overnightSurchargeApplied - discountAmount`, with `BigDecimal` and `HALF_UP` rounding to 2 decimal places.

All three reference cases from the spec (A: Rp 17,500.00, B: Rp 270,000.00, C: Rp 0.00) pass as unit tests and Case A also passes end-to-end over HTTP.

## Concurrency

Every operation on a lot's reservations and slots runs under a `ReentrantLock` per parking lot. This covers the writes (create, check-in, check-out, cancel, extend) and the availability query. For `createReservation`, finding a free slot and saving the reservation happen inside the same lock, so two requests cannot both see a slot as free and book it. `ReservationServiceImplTest` verifies this with 50 threads booking the only truck slot at the same moment: exactly one succeeds and 49 receive a conflict.

The availability query takes the same lock so that it always sees the latest changes from other threads and returns a consistent snapshot of the lot, never a mix of states from before and after a concurrent update. The list can still be out of date by the time the client books. This is why `createReservation` picks the slot itself and checks again under the lock, rather than trusting what the client saw. Both the availability query and `createReservation` look the lot up before locking, so requests for unknown lots never create a lock.

## Project Structure

```
src/main/java/com/parkease/
  domain/       entities, enums, value objects, validation guards
  service/      ReservationService, BillingService and implementations
  api/          REST controllers
  api/dto/      request and response DTOs with static from() mappers
  exception/    custom exceptions, ErrorResponse, GlobalExceptionHandler
  repository/   in-memory repositories
  config/       Clock bean, startup seed data
src/test/java/com/parkease/
  domain/       domain model tests
  service/      unit tests
  api/          API slice and integration tests
```

## Design Decisions

- **Layering:** `api` → `service` → `domain`. Controllers only parse requests, call the service and map results to DTOs. Domain objects are never returned directly.
- **ParkingLot owns its slots** (aggregate root). Slots are always looked up through their lot, so there is a single source of truth for slot status and no slot ID collision between lots. The lot keeps its own copy of the slot lists, which can't be changed from outside, and rejects duplicate slot IDs and slots listed under the wrong vehicle type. Slots themselves stay mutable because their status changes.
- **Always-valid domain objects:** constructors reject invalid data (blank IDs, negative amounts, end before start). State transitions throw `IllegalStateException` when called in the wrong status.
- **Status rules live in the enums:** `ReservationStatus.canTransitionTo` defines the whole reservation lifecycle in one place, and `Reservation` checks it before every transition. `blocksSlot()` decides which reservations count in conflict checks, and `canExtend()` restricts extensions to `ACTIVE`. `ParkingSlotStatus.isBookable()` takes `MAINTENANCE` slots out of availability. `VehicleType` stays a plain enum on purpose: prices are data in `RateCard`, and putting them in the enum would hard-code them.
- **Domain first, side effects after:** service methods call the domain transition before updating the slot, so a rejected operation leaves both the reservation and the slot unchanged.
- **Injected `Clock`:** late-cancellation checks and invoice timestamps use an injected `Clock`, so tests are deterministic.
- **Invoice consistency:** the invoice's parts and total use the same grace-period check, so `base + surcharge - discount` always equals `totalAmount`.

## Assumptions

- **Rounding per calendar day:** each day's billable minutes are rounded up to 30-minute blocks separately, as in the Case B worked example.
- **Midnight crossing:** a stay that ends exactly at 00:00 does not count as crossing midnight ("stays past midnight").
- **Daily cap:** applies to block charges only. The overnight surcharge is added on top, as rule 8 lists "daily charges and surcharges" separately.
- **Minute precision:** billing works in whole minutes; leftover seconds are ignored.
- **Discount:** the spec defines no discount rules, so `discountAmount` is always Rp 0.00.
- **Task 5 (Bonus):** the time guidance lists a Task 5 (Bonus), but the brief never describes it, so nothing was built for it. If it is about discounts, the invoice already has a `discountAmount` field and the total already subtracts it, so a discount rule would only need to set that amount.
- **Check-in window:** plannedStartTime ± 30 minutes. Earlier is rejected (422) because the previous booking may still occupy the slot. Later marks the reservation `NO_SHOW` and frees the slot.
- **Late cancellation:** cancelling at or after `plannedStartTime - 30 minutes` (based on the server clock) sets `lateCancellation = true`.
- **Extension:** only `ACTIVE` reservations can be extended, and `newEndTime` must be after the current `plannedEndTime`.
- **Floor levels:** `floorLevel` must be 0 or higher (no basements), so "prefer lower floors" and "prefer ground floor" give the same order.
- **Reservation IDs:** `RES-` followed by a UUID, matching the spec example.
- **Cancelling after check-in:** only `PENDING` reservations can be cancelled. The spec (4.2) also allows cancelling `ACTIVE` reservations, but an `ACTIVE` reservation has a vehicle parked in the slot, and cancelling it would free the slot and end the stay without an invoice. So cancelling an `ACTIVE` reservation is rejected (422) and the vehicle must check out instead, which bills the stay.
- **Cancellation reason:** required (non-blank). A `DELETE` without a body returns 400 with the message `Request body is required`.
- **MOTORCYCLE rates:** daily cap and overnight surcharge are not in the spec; assumed Rp 30,000 and Rp 10,000.

## Known Limitations and Trade-offs

- **In-memory storage:** data is lost on restart. A real deployment would use a database.
- **Single-instance locking:** the per-lot locks only work within one JVM. Running several instances would need database-level protection (row locks, unique constraints or optimistic locking).
- **Reads wait for writes:** availability queries share the per-lot lock with bookings, so reads on a busy lot run one at a time. A `ReentrantReadWriteLock` would let reads run in parallel if read traffic became heavy.
- **Slot status is a single current value** while reservations are time windows. Booking a slot for a later window while it is `OCCUPIED` overwrites the status to `RESERVED`, and cancelling a future booking marks the slot `AVAILABLE` even if another car is parked. Availability and conflict checks use reservation time windows (plus `MAINTENANCE`), not this status, so booking correctness is unaffected.
- **One unreadable value per response:** when a value in the body can't be read (for example an unknown enum), Jackson stops at that field and Bean Validation does not run, so only that one field is reported. Fixing it and resending shows any remaining errors. Reporting all of them at once would mean reading every field as a plain string first.
- **Availability time order is checked in the service:** an availability query with `endTime` not after `startTime` returns 400 with a message but no `fieldErrors`, because query parameters are not a request object that Bean Validation can compare.
- **Operating hours** are stored on each lot but not enforced; all seeded lots are open 24 hours.
- **No-show detection** only happens when a late check-in is attempted; there is no background job that expires reservations.
- **Overstay** past `plannedEndTime` is not blocked. Billing uses actual times, but the next booking on that slot may find it still occupied.
- **`ReservationRequest`** is used both as the API request body and as the service input, because the spec's service interface takes it directly. A stricter design would use a separate API DTO and map it.
- **No authentication or rate limiting.**
