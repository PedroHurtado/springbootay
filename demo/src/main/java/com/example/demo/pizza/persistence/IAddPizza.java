package com.example.demo.pizza.persistence;

import com.example.demo.core.repository.IAdd;
import com.example.demo.pizza.domain.Pizza;

/** Vista de "añadir pizza". Solo dominio, sin JPA. */
public interface IAddPizza extends IAdd<Pizza> {
}
