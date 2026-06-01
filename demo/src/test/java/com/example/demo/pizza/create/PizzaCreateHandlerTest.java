package com.example.demo.pizza.create;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.core.domain.EntityNotFoundException;
import com.example.demo.core.lookup.LookupResolver;
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.pizza.domain.Pizza;
import com.example.demo.pizza.persistence.IAddPizza;

@ExtendWith(MockitoExtension.class)
class PizzaCreateHandlerTest {

    @Mock
    private IAddPizza repository;

    @Mock
    private LookupResolver lookup;

    @InjectMocks
    private PizzaCreate.Handler handler;

    @Test
    void crea_pizza_con_ingredientes_resueltos_y_calcula_precio() {
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = Ingredient.create(ingredientId, "Tomate", new BigDecimal("0.50"));

        when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
                .thenReturn(Set.of(ingredient));

        var command = new PizzaCreate.Command(
                "Margherita", "Clásica", "http://img.png", Set.of(ingredientId));

        PizzaCreate.Response response = handler.handle(command);

        assertNotNull(response.id());
        assertEquals(new BigDecimal("0.60"), response.price()); // 0.50 * 1.20
        verify(repository).add(org.mockito.ArgumentMatchers.any(Pizza.class));
    }

    @Test
    void lanza_si_un_ingrediente_no_existe() {
        UUID ingredientId = UUID.randomUUID();
        when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
                .thenThrow(new EntityNotFoundException(Ingredient.class, ingredientId));

        var command = new PizzaCreate.Command(
                "X", "Y", "http://z.png", Set.of(ingredientId));

        assertThrows(EntityNotFoundException.class, () -> handler.handle(command));
    }
}
