package com.example.demo.pizza.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repositorio Spring Data de {@link PizzaJpa}. Confinado a {@link PizzaRepository}. */
public interface PizzaJpaRepository extends JpaRepository<PizzaJpa, UUID> {

    /** Carga la pizza y sus ingredientes en una sola query (evita la LazyInitializationException). */
    @Query("SELECT p FROM PizzaJpa p LEFT JOIN FETCH p.ingredients WHERE p.id = :id")
    Optional<PizzaJpa> findByIdWithIngredients(@Param("id") UUID id);

    /** Lista todas las pizzas con sus ingredientes cargados (para el slice de listado). */
    @Query("SELECT DISTINCT p FROM PizzaJpa p LEFT JOIN FETCH p.ingredients")
    List<PizzaJpa> findAllWithIngredients();
}
