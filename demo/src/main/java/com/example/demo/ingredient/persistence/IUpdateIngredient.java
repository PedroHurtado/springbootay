package com.example.demo.ingredient.persistence;

import java.util.UUID;

import com.example.demo.core.repository.IUpdate;
import com.example.demo.ingredient.domain.Ingredient;

/** Vista de "actualizar ingrediente". Hereda {@code get} de IUpdate &rarr; IGet. */
public interface IUpdateIngredient extends IUpdate<Ingredient, UUID> {
}
