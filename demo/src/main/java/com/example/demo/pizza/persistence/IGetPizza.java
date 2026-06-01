package com.example.demo.pizza.persistence;

import java.util.UUID;

import com.example.demo.core.repository.IGet;
import com.example.demo.pizza.domain.Pizza;

/** Vista de "leer pizza por id". {@code get} lanza si no existe. */
public interface IGetPizza extends IGet<Pizza, UUID> {
}
