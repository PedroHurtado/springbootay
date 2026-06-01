package com.example.demo.core.web;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.core.domain.DomainException;
import com.example.demo.core.domain.EntityNotFoundException;
import com.example.demo.core.lookup.NoLookupRegisteredException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Traduce las excepciones de dominio a Problem Details (RFC 9457).
 *
 * <ul>
 *   <li>{@link EntityNotFoundException} &rarr; 404 Not Found.</li>
 *   <li>Cualquier otra {@link DomainException} (invariante de negocio) &rarr; 422.</li>
 * </ul>
 */
@RestControllerAdvice
public class DomainExceptionAdvice {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Entidad no encontrada");
        problem.setType(URI.create("https://demo.local/problems/not-found"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        problem.setTitle("Violación de regla de dominio");
        problem.setType(URI.create("https://demo.local/problems/domain"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(NoLookupRegisteredException.class)
    public ProblemDetail handleNoLookup(NoLookupRegisteredException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
        problem.setTitle("Configuración incompleta");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
