package com.example.demo.ingredient.persistence;

import java.util.UUID;

import com.example.demo.core.repository.IGet;
import com.example.demo.ingredient.domain.Ingredient;

/** Vista de "leer ingrediente por id". {@code get} lanza si no existe. */
public interface IGetIngredient extends IGet<Ingredient, UUID> {
}
