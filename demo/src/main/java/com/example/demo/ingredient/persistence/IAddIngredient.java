package com.example.demo.ingredient.persistence;

import com.example.demo.core.repository.IAdd;
import com.example.demo.ingredient.domain.Ingredient;

/** Vista de "añadir ingrediente" sobre el repositorio. Solo dominio, sin JPA. */
public interface IAddIngredient extends IAdd<Ingredient> {
}
