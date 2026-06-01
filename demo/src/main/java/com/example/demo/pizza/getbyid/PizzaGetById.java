package com.example.demo.pizza.getbyid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.core.dispatcher.QueryDispatcher;
import com.example.demo.core.dispatcher.QueryHandler;
import com.example.demo.pizza.domain.Pizza;
import com.example.demo.pizza.persistence.IGetPizza;

/** Slice de lectura de una Pizza por id (con ingredientes y precio calculado). */
public class PizzaGetById {

    private PizzaGetById() {
    }

    @RestController
    @RequestMapping("/api/pizzas")
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

        private final IGetPizza repository;

        public Handler(IGetPizza repository) {
            this.repository = repository;
        }

        @Override
        public Class<Query> queryType() {
            return Query.class;
        }

        @Override
        public Response handle(Query query) {
            Pizza pizza = repository.get(query.id());   // lanza 404 si no existe
            return toResponse(pizza);
        }
    }

    static Response toResponse(Pizza pizza) {
        List<IngredientView> ingredients = pizza.getIngredients().stream()
                .map(i -> new IngredientView(i.getId(), i.getName(), i.getCost()))
                .toList();
        return new Response(
                pizza.getId(),
                pizza.getName(),
                pizza.getDescription(),
                pizza.getUrl(),
                pizza.getPrice(),
                ingredients);
    }

    public record Query(UUID id) {
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
