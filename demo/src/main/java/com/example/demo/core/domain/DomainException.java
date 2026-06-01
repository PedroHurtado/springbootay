package com.example.demo.core.domain;

/**
 * Raíz de la jerarquía de excepciones de dominio. Representa la violación de una
 * invariante del modelo (regla de negocio), no un error de forma del input.
 * El handler web la traduce a 422 Unprocessable Entity.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
