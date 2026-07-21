package dev.deepdive.transaction.presentation.reservation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.deepdive.transaction.application.reservation.ReservationNotFoundException;
import dev.deepdive.transaction.application.reservation.ReservationResult;
import dev.deepdive.transaction.application.reservation.ReservationService;
import dev.deepdive.transaction.domain.reservation.ReservationStatus;
import dev.deepdive.transaction.domain.seat.NotEnoughSeatsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReservationControllerTest {
    private ReservationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ReservationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReservationController(service))
                .setControllerAdvice(new ReservationExceptionHandler())
                .build();
    }

    @Test
    void createReturnsReservationId() throws Exception {
        when(service.create(any())).thenReturn(new ReservationResult(1L, 0, ReservationStatus.CREATED));

        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(1))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void unavailableReservationReturnsConflict() throws Exception {
        when(service.place(1L)).thenThrow(new NotEnoughSeatsException());

        mockMvc.perform(post("/reservations/1/place"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("not enough seats"));
    }

    @Test
    void unknownReservationReturnsNotFound() throws Exception {
        when(service.place(999L)).thenThrow(new ReservationNotFoundException(999L));

        mockMvc.perform(post("/reservations/999/place"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void invalidCreateRequestReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 0,
                                  "workshopId": 0,
                                  "seatCount": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private String validCreateRequest() {
        return """
                {
                  "userId": 1,
                  "workshopId": 100,
                  "seatCount": 2
                }
                """;
    }
}
