package com.example.demo.ingredient.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.demo.core.repository.Mapper;
import com.example.demo.ingredient.domain.Ingredient;

/**
 * Mapper manual Ingredient &harr; IngredientJpa.
 *
 * <p>{@code toDomain} reconstruye el dominio mediante la subclase {@link Hydration},
 * que invoca el constructor {@code protected} de {@link Ingredient}. Así se hidrata
 * desde persistencia respetando la encapsulación, sin usar la fábrica {@code create}.
 * Ingredient no emite eventos, pero aplicamos el mismo patrón que en Pizza por
 * uniformidad: la frontera de reconstrucción es siempre el constructor protected.</p>
 */
@Component
public class IngredientMapper implements Mapper<Ingredient, IngredientJpa> {

    @Override
    public IngredientJpa toJpa(Ingredient ingredient) {
        return new IngredientJpa(
                ingredient.getId(),
                ingredient.getName(),
                ingredient.getCost());
    }

    @Override
    public Ingredient toDomain(IngredientJpa jpa) {
        return new Hydration(jpa.getId(), jpa.getName(), jpa.getCost());
    }

    /** Subclase de hidratación: acceso al constructor protected del dominio. */
    private static final class Hydration extends Ingredient {
        private Hydration(UUID id, String name, BigDecimal cost) {
            super(id, name, cost);
        }
    }
}
