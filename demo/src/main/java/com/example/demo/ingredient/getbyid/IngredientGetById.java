package com.example.demo.ingredient.getbyid;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.core.dispatcher.QueryDispatcher;
import com.example.demo.core.dispatcher.QueryHandler;
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.ingredient.persistence.IGetIngredient;

/** Slice de lectura de un Ingredient por id. */
public class IngredientGetById {

    private IngredientGetById() {
    }

    @RestController
    @RequestMapping("/api/ingredients")
    public static class Endpoint {

        private final QueryDispatcher dispatcher;

        public Endpoint(QueryDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @GetMapping("/{id}")
        public Response getById(@PathVariable UUID id) {
            return dispatcher.dispatch(new Query(id));
        }
    }

    @Service
    public static class Handler implements QueryHandler<Query, Response> {

        private final IGetIngredient repository;

        public Handler(IGetIngredient repository) {
            this.repository = repository;
        }

        @Override
        public Class<Query> queryType() {
            return Query.class;
        }

        @Override
        public Response handle(Query query) {
            Ingredient ingredient = repository.get(query.id());   // lanza 404 si no existe
            return new Response(ingredient.getId(), ingredient.getName(), ingredient.getCost());
        }
    }

    public record Query(UUID id) {
    }

    public record Response(UUID id, String name, BigDecimal cost) {
    }
}
