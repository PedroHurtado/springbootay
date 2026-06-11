package com.example.demo.ingredient.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import an.awesome.pipelinr.Pipeline;

import com.example.demo.core.repository.IAddJpa;
import com.example.demo.core.repository.IRemoveJpa;
import com.example.demo.core.repository.IUpdateJpa;
import com.example.demo.core.repository.Mapper;
import com.example.demo.ingredient.domain.Ingredient;

/**
 * Repositorio de Ingredient. Solo cableado: la lógica de add/get/update/remove vive
 * en los {@code default methods} de las interfaces {@code *Jpa}. Implementa además
 * las vistas de dominio ({@code IAddIngredient}...) para que Spring lo inyecte bajo
 * cada una de ellas.
 */
@Repository
public class IngredientRepository implements
        IAddIngredient, IGetIngredient, IUpdateIngredient, IRemoveIngredient,
        IAddJpa<Ingredient, UUID, IngredientJpa>,
        IUpdateJpa<Ingredient, UUID, IngredientJpa>,
        IRemoveJpa<Ingredient, UUID, IngredientJpa> {

    private final IngredientJpaRepository jpa;
    private final IngredientMapper mapper;
    private final Pipeline pipeline;

    public IngredientRepository(IngredientJpaRepository jpa, IngredientMapper mapper, Pipeline pipeline) {
        this.jpa = jpa;
        this.mapper = mapper;
        this.pipeline = pipeline;
    }

    @Override
    public JpaRepository<IngredientJpa, UUID> jpa() {
        return jpa;
    }

    @Override
    public Mapper<Ingredient, IngredientJpa> mapper() {
        return mapper;
    }

    @Override
    public Pipeline pipeline() {
        return pipeline;
    }

    @Override
    public Class<Ingredient> domainType() {
        return Ingredient.class;
    }
}
