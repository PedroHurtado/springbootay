package com.example.demo.ingredient.persistence;

import java.util.UUID;

import com.example.demo.core.repository.IRemove;
import com.example.demo.ingredient.domain.Ingredient;

/** Vista de "borrar ingrediente". Hereda {@code get} de IRemove &rarr; IGet. */
public interface IRemoveIngredient extends IRemove<Ingredient, UUID> {
}
