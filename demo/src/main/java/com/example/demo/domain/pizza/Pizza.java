package com.example.demo.domain.pizza;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.example.demo.domain.AggregateBase;

public class Pizza extends AggregateBase {

    private static final BigDecimal PROFIT_MARGIN = new BigDecimal("1.20");

    private String name;
    private String description;
    private String url;
    private final Set<Ingredient> ingredients = new HashSet<>();

    protected Pizza(UUID id, String name, String description, String url, Set<Ingredient> ingredients) {
        super(id);
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.url = Objects.requireNonNull(url, "url");
        this.ingredients.addAll(Objects.requireNonNull(ingredients, "ingredients"));
    }

    public static Pizza create(UUID id, String name, String description, String url, Set<Ingredient> ingredients) {
        var pizza = new Pizza(id, name, description, url, ingredients);
        pizza.add("pizza.create", pizza);
        return pizza;
    }

    public void update(String name, String description, String url, Set<Ingredient> ingredients) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.url = Objects.requireNonNull(url, "url");
        this.ingredients.clear();
        this.ingredients.addAll(Objects.requireNonNull(ingredients, "ingredients"));
        add("pizza.update", this);
    }

    public void addIngredient(Ingredient ingredient) {
        ingredients.add(Objects.requireNonNull(ingredient, "ingredient"));
    }

    public void removeIngredient(Ingredient ingredient) {
        ingredients.remove(Objects.requireNonNull(ingredient, "ingredient"));
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getUrl() {
        return url;
    }

    public Set<Ingredient> getIngredients() {
        return Collections.unmodifiableSet(ingredients);
    }

    public BigDecimal getPrice() {
        BigDecimal cost = ingredients.stream()
                .map(Ingredient::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return cost.multiply(PROFIT_MARGIN).setScale(2, RoundingMode.HALF_UP);
    }
}
