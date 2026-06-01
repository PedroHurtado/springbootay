package com.example.demo.pizza.persistence;

import java.util.UUID;

import com.example.demo.core.repository.IUpdate;
import com.example.demo.pizza.domain.Pizza;

/** Vista de "actualizar pizza". Hereda {@code get} de IUpdate &rarr; IGet. */
public interface IUpdatePizza extends IUpdate<Pizza, UUID> {
}
