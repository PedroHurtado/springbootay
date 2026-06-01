package com.example.demo.pizza.getall;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.core.dispatcher.QueryDispatcher;
import com.example.demo.core.dispatcher.QueryHandler;
import com.example.demo.pizza.persistence.PizzaJpaRepository;
import com.example.demo.pizza.persistence.PizzaMapper;

/** Slice de listado de Pizzas. Usa Spring Data directamente con los ingredientes cargados. */
public class PizzaGetAll {

    private PizzaGetAll() {
    }

    @RestController
    @RequestMapping("/api/pizzas")
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

        private final PizzaJpaRepository jpa;
        private final PizzaMapper mapper;

        public Handler(PizzaJpaRepository jpa, PizzaMapper mapper) {
            this.jpa = jpa;
            this.mapper = mapper;
        }

        @Override
        public Class<Query> queryType() {
            return Query.class;
        }

        @Override
        @Transactional(readOnly = true)
        public List<Response> handle(Query query) {
            return jpa.findAllWithIngredients().stream()
                    .map(mapper::toDomain)
                    .map(p -> {
                        List<IngredientView> ingredients = p.getIngredients().stream()
                                .map(i -> new IngredientView(i.getId(), i.getName(), i.getCost()))
                                .toList();
                        return new Response(p.getId(), p.getName(), p.getDescription(),
                                p.getUrl(), p.getPrice(), ingredients);
                    })
                    .toList();
        }
    }

    public record Query() {
    }

    public record Response(
            UUID id,
            String name,
            String description,
            String url,
            BigDecimal price,
            List<IngredientView> ingredients) {
    }

    public record IngredientView(UUID id, String name, BigDecimal cost) {
    }
}
