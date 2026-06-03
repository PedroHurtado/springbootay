package com.example.demo.ingredient.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class IngredientTest {

    @Test
    void create_conDatosValidos_creaIngrediente() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = Ingredient.create(id, "Tomate", new BigDecimal("0.50"));

        assertNotNull(ingredient);
        assertEquals(id, ingredient.getId());
        assertEquals("Tomate", ingredient.getName());
        assertEquals(new BigDecimal("0.50"), ingredient.getCost());
    }

    @Test
    void create_conNombreNull_lanzaExcepcion() {
        UUID id = UUID.randomUUID();

        InvalidIngredientException ex = assertThrows(
                InvalidIngredientException.class,
                () -> Ingredient.create(id, null, new BigDecimal("0.50")));

        assertEquals("El nombre es obligatorio", ex.getMessage());
    }

    @Test
    void create_conNombreEnBlanco_lanzaExcepcion() {
        UUID id = UUID.randomUUID();

        InvalidIngredientException ex = assertThrows(
                InvalidIngredientException.class,
                () -> Ingredient.create(id, "   ", new BigDecimal("0.50")));

        assertEquals("El nombre es obligatorio", ex.getMessage());
    }

    @Test
    void create_conNombreDemasiadoLargo_lanzaExcepcion() {
        UUID id = UUID.randomUUID();
        String nombre = "a".repeat(101);

        InvalidIngredientException ex = assertThrows(
                InvalidIngredientException.class,
                () -> Ingredient.create(id, nombre, new BigDecimal("0.50")));

        assertEquals("El nombre no puede superar 100 caracteres", ex.getMessage());
    }

    @Test
    void create_conNombreDe100Caracteres_creaIngrediente() {
        UUID id = UUID.randomUUID();
        String nombre = "a".repeat(100);

        Ingredient ingredient = Ingredient.create(id, nombre, new BigDecimal("0.50"));

        assertEquals(nombre, ingredient.getName());
    }

    @Test
    void create_conCosteNull_lanzaExcepcion() {
        UUID id = UUID.randomUUID();

        InvalidIngredientException ex = assertThrows(
                InvalidIngredientException.class,
                () -> Ingredient.create(id, "Tomate", null));

        assertEquals("El coste es obligatorio", ex.getMessage());
    }

    @Test
    void create_conCosteNegativo_lanzaExcepcion() {
        UUID id = UUID.randomUUID();

        InvalidIngredientException ex = assertThrows(
                InvalidIngredientException.class,
                () -> Ingredient.create(id, "Tomate", new BigDecimal("-0.01")));

        assertEquals("El coste no puede ser negativo", ex.getMessage());
    }

    @Test
    void create_conCosteCero_creaIngrediente() {
        UUID id = UUID.randomUUID();

        Ingredient ingredient = Ingredient.create(id, "Tomate", BigDecimal.ZERO);

        assertEquals(BigDecimal.ZERO, ingredient.getCost());
    }

    @Test
    void update_conDatosValidos_actualizaCampos() {
        UUID id = UUID.randomUUID();
        Ingredient ingredient = Ingredient.create(id, "Tomate", new BigDecimal("0.50"));

        ingredient.update("Queso", new BigDecimal("1.20"));

        assertEquals("Queso", ingredient.getName());
        assertEquals(new BigDecimal("1.20"), ingredient.getCost());
    }
}
