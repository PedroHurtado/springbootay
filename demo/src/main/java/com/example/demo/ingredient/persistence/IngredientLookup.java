package com.example.demo.ingredient.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.demo.core.lookup.Lookup;
import com.example.demo.ingredient.domain.Ingredient;

/**
 * Permite resolver un {@link Ingredient} por id desde otros agregados (Pizza).
 * El {@link com.example.demo.core.lookup.LookupResolver} lo recoge automáticamente.
 */
@Component
public class IngredientLookup implements Lookup<Ingredient> {

    private final IGetIngredient repository;

    public IngredientLookup(IGetIngredient repository) {
        this.repository = repository;
    }

    @Override
    public Class<Ingredient> type() {
        return Ingredient.class;
    }

    @Override
    public Ingredient find(UUID id) {
        return repository.get(id);   // ya lanza EntityNotFoundException
    }
}
