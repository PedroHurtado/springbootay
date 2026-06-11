package com.example.demo.pizza.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.example.demo.core.config.PipelinrConfig;
import com.example.demo.core.domain.EntityNotFoundException;
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.ingredient.persistence.IngredientJpa;
import com.example.demo.ingredient.persistence.IngredientJpaRepository;
import com.example.demo.ingredient.persistence.IngredientMapper;
import com.example.demo.pizza.domain.Pizza;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@Import({ PizzaRepository.class, PizzaMapper.class, IngredientMapper.class, PipelinrConfig.class })
class PizzaRepositoryTest {

    @Autowired
    private PizzaRepository repository;

    @Autowired
    private IngredientJpaRepository ingredientJpa;

    @PersistenceContext
    private EntityManager em;

    @Test
    void add_y_get_devuelven_la_pizza_con_sus_ingredientes() {
        // El ingrediente ya existe en BD (es target de FK)
        UUID ingredientId = UUID.randomUUID();
        ingredientJpa.saveAndFlush(new IngredientJpa(ingredientId, "Mozzarella", new BigDecimal("1.50")));

        Ingredient ingredient = Ingredient.create(ingredientId, "Mozzarella", new BigDecimal("1.50"));
        Pizza pizza = Pizza.create(UUID.randomUUID(), "Margherita", "Clásica",
                "http://img/margherita.png", Set.of(ingredient));

        repository.add(pizza);
        em.flush();
        em.clear();

        Pizza loaded = repository.get(pizza.getId());

        assertEquals(pizza.getId(), loaded.getId());
        assertEquals("Margherita", loaded.getName());
        assertEquals(1, loaded.getIngredients().size());
        // precio = 1.50 * 1.20 = 1.80
        assertEquals(new BigDecimal("1.80"), loaded.getPrice());
    }

    @Test
    void get_lanza_si_no_existe() {
        assertThrows(EntityNotFoundException.class, () -> repository.get(UUID.randomUUID()));
    }

    @Test
    void toDomain_no_genera_eventos_al_hidratar() {
        UUID ingredientId = UUID.randomUUID();
        ingredientJpa.saveAndFlush(new IngredientJpa(ingredientId, "Tomate", new BigDecimal("0.50")));

        Ingredient ingredient = Ingredient.create(ingredientId, "Tomate", new BigDecimal("0.50"));
        Pizza pizza = Pizza.create(UUID.randomUUID(), "Marinara", "Sin queso",
                "http://img/marinara.png", Set.of(ingredient));
        repository.add(pizza);
        em.flush();
        em.clear();

        Pizza loaded = repository.get(pizza.getId());

        // Reconstruida vía constructor protected (subclase de hidratación): sin eventos
        assertTrue(loaded.getEvents().isEmpty());
    }
}
