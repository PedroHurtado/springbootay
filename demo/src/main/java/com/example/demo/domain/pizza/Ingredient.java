package com.example.demo.domain.pizza;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import com.example.demo.domain.EntityBase;

public class Ingredient extends EntityBase {

    private String name;
    private BigDecimal cost;

    protected Ingredient(UUID id, String name, BigDecimal cost) {
        super(id);
        this.name = Objects.requireNonNull(name, "name");
        this.cost = Objects.requireNonNull(cost, "cost");
    }

    public static Ingredient create(UUID id, String name, BigDecimal cost) {
        return new Ingredient(id, name, cost);
    }

    public void update(String name, BigDecimal cost) {
        this.name = Objects.requireNonNull(name, "name");
        this.cost = Objects.requireNonNull(cost, "cost");
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCost() {
        return cost;
    }
}
