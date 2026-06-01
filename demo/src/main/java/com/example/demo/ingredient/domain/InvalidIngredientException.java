package com.example.demo.ingredient.domain;

import com.example.demo.core.domain.DomainException;

/** Invariante de {@link Ingredient} violada. El handler web la traduce a 422. */
public class InvalidIngredientException extends DomainException {

    public InvalidIngredientException(String message) {
        super(message);
    }
}
