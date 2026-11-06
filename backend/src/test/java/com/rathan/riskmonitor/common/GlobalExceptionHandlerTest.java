package com.rathan.riskmonitor.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/test");
    }

    @Test
    void testHandleNotFound() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new ResourceNotFoundException("Not found"), request);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Not found", response.getBody().getMessage());
    }

    @Test
    void testHandleConflict() {
        ResponseEntity<ErrorResponse> response = handler.handleConflict(new ConflictException("Conflict"), request);
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Conflict", response.getBody().getMessage());
    }

    @Test
    void testHandleBadRequest() {
        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(new BadRequestException("Bad request"), request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad request", response.getBody().getMessage());
    }

    @Test
    void testHandleAccessDenied() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(new AccessDeniedException("Forbidden"), request);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void testHandleAuthenticationException() {
        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(new BadCredentialsException("Bad creds"), request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testHandleGenericException() {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(new RuntimeException("Oops"), request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Oops", response.getBody().getMessage());

        ResponseEntity<ErrorResponse> nullMsgResponse = handler.handleGenericException(new RuntimeException((String) null), request);
        assertEquals("An unexpected server error occurred", nullMsgResponse.getBody().getMessage());
    }

    @Test
    void testHandleValidationErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(new FieldError("obj", "amount", "must not be null")));

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex, request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().getDetails());
        assertEquals(1, response.getBody().getDetails().size());
    }
}
