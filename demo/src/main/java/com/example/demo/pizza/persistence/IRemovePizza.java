package com.example.demo.pizza.persistence;

import java.util.UUID;

import com.example.demo.core.repository.IRemove;
import com.example.demo.pizza.domain.Pizza;

/** Vista de "borrar pizza". Hereda {@code get} de IRemove &rarr; IGet. */
public interface IRemovePizza extends IRemove<Pizza, UUID> {
}
