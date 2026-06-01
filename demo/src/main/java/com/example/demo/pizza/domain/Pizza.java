package com.example.demo.pizza.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.example.demo.core.domain.AggregateBase;
import com.example.demo.ingredient.domain.Ingredient;

/**
 * Pizza. Raíz de agregado: emite eventos de dominio. Always-valid.
 *
 * <p>El precio se calcula como la suma del coste de los ingredientes más un 20% de
 * margen (ver {@code docs/01--rest.txt}).</p>
 *
 * <p>El constructor es {@code protected} y NO emite eventos: es el punto de
 * reconstrucción desde persistencia (la subclase de hidratación del mapper lo usa).
 * La fábrica {@link #create} es quien emite {@code pizza.create}.</p>
 */
public class Pizza extends AggregateBase {

    private static final BigDecimal PROFIT_MARGIN = new BigDecimal("1.20");

    private String name;
    private String description;
    private String url;
    private final Set<Ingredient> ingredients = new HashSet<>();

    protected Pizza(UUID id, String name, String description, String url, Set<Ingredient> ingredients) {
        super(id);
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.url = validateUrl(url);
        setIngredients(ingredients);
    }

    public static Pizza create(UUID id, String name, String description, String url,
            Set<Ingredient> ingredients) {
        Pizza pizza = new Pizza(id, name, description, url, ingredients);
        pizza.add("pizza.create", pizza);
        return pizza;
    }

    public void update(String name, String description, String url, Set<Ingredient> ingredients) {
        this.name = validateName(name);
        this.description = validateDescription(description);
        this.url = validateUrl(url);
        setIngredients(ingredients);
        add("pizza.update", this);
    }

    private void setIngredients(Set<Ingredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            throw new InvalidPizzaException("La pizza necesita al menos un ingrediente");
        }
        this.ingredients.clear();
        this.ingredients.addAll(ingredients);
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidPizzaException("El nombre es obligatorio");
        }
        if (name.length() > 100) {
            throw new InvalidPizzaException("El nombre no puede superar 100 caracteres");
        }
        return name;
    }

    private static String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new InvalidPizzaException("La descripción es obligatoria");
        }
        return description;
    }

    private static String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidPizzaException("La url es obligatoria");
        }
        return url;
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

    /** Precio = suma del coste de los ingredientes + 20% de margen, con 2 decimales. */
    public BigDecimal getPrice() {
        BigDecimal cost = ingredients.stream()
                .map(Ingredient::getCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return cost.multiply(PROFIT_MARGIN).setScale(2, RoundingMode.HALF_UP);
    }
}
