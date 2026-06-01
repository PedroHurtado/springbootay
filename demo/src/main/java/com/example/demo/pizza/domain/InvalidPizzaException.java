package com.example.demo.pizza.domain;

import com.example.demo.core.domain.DomainException;

/** Invariante de {@link Pizza} violada. El handler web la traduce a 422. */
public class InvalidPizzaException extends DomainException {

    public InvalidPizzaException(String message) {
        super(message);
    }
}
