package com.example.demo.pizza.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.core.domain.EntityNotFoundException;
import com.example.demo.core.repository.IAddJpa;
import com.example.demo.core.repository.IRemoveJpa;
import com.example.demo.core.repository.IUpdateJpa;
import com.example.demo.core.repository.Mapper;
import com.example.demo.pizza.domain.Pizza;

/**
 * Repositorio de Pizza. add/update/remove provienen de los {@code default methods}
 * de las interfaces {@code *Jpa}; solo se sobrescribe {@code get} para cargar el
 * agregado completo con un JOIN FETCH (evita {@code LazyInitializationException} al
 * mapear los ingredientes). Sobrescribir un default es idéntico a sobrescribir un
 * método heredado: el design lo permite cuando un caso lo necesita.
 */
@Repository
public class PizzaRepository implements
        IAddPizza, IGetPizza, IUpdatePizza, IRemovePizza,
        IAddJpa<Pizza, UUID, PizzaJpa>,
        IUpdateJpa<Pizza, UUID, PizzaJpa>,
        IRemoveJpa<Pizza, UUID, PizzaJpa> {

    private final PizzaJpaRepository jpa;
    private final PizzaMapper mapper;

    public PizzaRepository(PizzaJpaRepository jpa, PizzaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public JpaRepository<PizzaJpa, UUID> jpa() {
        return jpa;
    }

    @Override
    public Mapper<Pizza, PizzaJpa> mapper() {
        return mapper;
    }

    @Override
    public Class<Pizza> domainType() {
        return Pizza.class;
    }

    @Override
    @Transactional(readOnly = true)
    public Pizza get(UUID id) {
        return jpa.findByIdWithIngredients(id)
                .map(mapper::toDomain)
                .orElseThrow(() -> new EntityNotFoundException(Pizza.class, id));
    }
}
