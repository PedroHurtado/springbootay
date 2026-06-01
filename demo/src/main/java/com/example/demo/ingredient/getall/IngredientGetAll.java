package com.example.demo.ingredient.getall;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.core.dispatcher.QueryDispatcher;
import com.example.demo.core.dispatcher.QueryHandler;
import com.example.demo.ingredient.persistence.IngredientJpaRepository;
import com.example.demo.ingredient.persistence.IngredientMapper;

/**
 * Slice de listado de Ingredients. Las consultas de listado usan Spring Data
 * directamente (no las interfaces segregadas de escritura).
 */
public class IngredientGetAll {

    private IngredientGetAll() {
    }

    @RestController
    @RequestMapping("/api/ingredients")
    public static class Endpoint {

        private final QueryDispatcher dispatcher;

        public Endpoint(QueryDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @GetMapping
        public List<Response> getAll() {
            return dispatcher.dispatch(new Query());
        }
    }

    @Service
    public static class Handler implements QueryHandler<Query, List<Response>> {

        private final IngredientJpaRepository jpa;
        private final IngredientMapper mapper;

        public Handler(IngredientJpaRepository jpa, IngredientMapper mapper) {
            this.jpa = jpa;
            this.mapper = mapper;
        }

        @Override
        public Class<Query> queryType() {
            return Query.class;
        }

        @Override
        public List<Response> handle(Query query) {
            return jpa.findAll().stream()
                    .map(mapper::toDomain)
                    .map(i -> new Response(i.getId(), i.getName(), i.getCost()))
                    .toList();
        }
    }

    public record Query() {
    }

    public record Response(UUID id, String name, BigDecimal cost) {
    }
}
