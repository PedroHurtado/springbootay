package com.example.demo.ingredient.persistence;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entidad JPA de Ingredient. Mundo paralelo al dominio: constructor sin argumentos
 * para JPA, campos mutables, sin validaciones ni eventos.
 */
@Entity
@Table(name = "ingredient", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class IngredientJpa {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    protected IngredientJpa() {
        // requerido por JPA
    }

    public IngredientJpa(UUID id, String name, BigDecimal cost) {
        this.id = id;
        this.name = name;
        this.cost = cost;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }
}
