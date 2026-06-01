package com.example.demo.ingredient.domain;

import java.math.BigDecimal;
import java.util.UUID;

import com.example.demo.core.domain.EntityBase;

/**
 * Ingrediente. Entidad de dominio (no raíz de agregado: no emite eventos).
 * Always-valid: si existe una instancia, es válida.
 *
 * <p>El constructor es {@code protected} para que la capa de persistencia pueda
 * reconstruir la entidad mediante una subclase de hidratación, sin pasar por la
 * fábrica {@link #create}.</p>
 */
public class Ingredient extends EntityBase {

    private String name;
    private BigDecimal cost;

    protected Ingredient(UUID id, String name, BigDecimal cost) {
        super(id);
        this.name = validateName(name);
        this.cost = validateCost(cost);
    }

    public static Ingredient create(UUID id, String name, BigDecimal cost) {
        return new Ingredient(id, name, cost);
    }

    public void update(String name, BigDecimal cost) {
        this.name = validateName(name);
        this.cost = validateCost(cost);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidIngredientException("El nombre es obligatorio");
        }
        if (name.length() > 100) {
            throw new InvalidIngredientException("El nombre no puede superar 100 caracteres");
        }
        return name;
    }

    private static BigDecimal validateCost(BigDecimal cost) {
        if (cost == null) {
            throw new InvalidIngredientException("El coste es obligatorio");
        }
        if (cost.signum() < 0) {
            throw new InvalidIngredientException("El coste no puede ser negativo");
        }
        return cost;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
