package com.example.demo.pizza.persistence;

import static java.util.stream.Collectors.toSet;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.demo.core.repository.Mapper;
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.ingredient.persistence.IngredientJpa;
import com.example.demo.ingredient.persistence.IngredientMapper;
import com.example.demo.pizza.domain.Pizza;

/**
 * Mapper manual Pizza &harr; PizzaJpa. Se compone con {@link IngredientMapper} para
 * mapear la colección de ingredientes.
 *
 * <p>{@code toDomain} reconstruye la pizza con la subclase {@link Hydration}, que
 * invoca el constructor {@code protected} de {@link Pizza}. Ese constructor NO emite
 * eventos, de modo que reconstruir desde BD no dispara {@code pizza.create}. Esta es
 * la alternativa, propuesta en clase, al {@code clearEvents()} tras la fábrica.</p>
 */
@Component
public class PizzaMapper implements Mapper<Pizza, PizzaJpa> {

    private final IngredientMapper ingredientMapper;

    public PizzaMapper(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    @Override
    public PizzaJpa toJpa(Pizza pizza) {
        Set<IngredientJpa> ingredients = pizza.getIngredients().stream()
                .map(ingredientMapper::toJpa)
                .collect(toSet());

        return new PizzaJpa(
                pizza.getId(),
                pizza.getName(),
                pizza.getDescription(),
                pizza.getUrl(),
                ingredients);
    }

    @Override
    public Pizza toDomain(PizzaJpa jpa) {
        Set<Ingredient> ingredients = jpa.getIngredients().stream()
                .map(ingredientMapper::toDomain)
                .collect(toSet());

        return new Hydration(
                jpa.getId(),
                jpa.getName(),
                jpa.getDescription(),
                jpa.getUrl(),
                ingredients);
    }

    /** Subclase de hidratación: invoca el constructor protected &rarr; sin eventos. */
    private static final class Hydration extends Pizza {
        private Hydration(UUID id, String name, String description, String url, Set<Ingredient> ingredients) {
            super(id, name, description, url, ingredients);
        }
    }
}
