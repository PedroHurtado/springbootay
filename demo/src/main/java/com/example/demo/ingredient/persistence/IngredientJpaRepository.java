package com.example.demo.ingredient.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data de {@link IngredientJpa}. Acceso "pleno" a JPA confinado:
 * nadie lo inyecta excepto {@link IngredientRepository}.
 */
public interface IngredientJpaRepository extends JpaRepository<IngredientJpa, UUID> {

    boolean existsByName(String name);
}
