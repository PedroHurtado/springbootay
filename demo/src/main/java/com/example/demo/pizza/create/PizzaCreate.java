package com.example.demo.pizza.create;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.core.dispatcher.CommandDispatcher;
import com.example.demo.core.dispatcher.CommandHandler;
import com.example.demo.core.lookup.LookupResolver;
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.pizza.domain.Pizza;
import com.example.demo.pizza.persistence.IAddPizza;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Slice de creación de Pizza. */
public class PizzaCreate {

    private PizzaCreate() {
    }

    @RestController
    @RequestMapping("/api/pizzas")
    public static class Endpoint {

        private final CommandDispatcher dispatcher;

        public Endpoint(CommandDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @PostMapping
        public ResponseEntity<Response> create(@Valid @RequestBody Command command) {
            Response response = dispatcher.dispatch(command);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }

    @Service
    public static class Handler implements CommandHandler<Command, Response> {

        private final IAddPizza repository;
        private final LookupResolver lookup;

        public Handler(IAddPizza repository, LookupResolver lookup) {
            this.repository = repository;
            this.lookup = lookup;
        }

        @Override
        public Class<Command> commandType() {
            return Command.class;
        }

        @Override
        public Response handle(Command command) {
            Set<Ingredient> ingredients = lookup.findAll(Ingredient.class, command.ingredientIds());

            Pizza pizza = Pizza.create(
                    UUID.randomUUID(),
                    command.name(),
                    command.description(),
                    command.url(),
                    ingredients);

            repository.add(pizza);
            return new Response(pizza.getId(), pizza.getPrice());
        }
    }

    public record Command(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
            String name,

            @NotBlank(message = "La descripción es obligatoria")
            String description,

            @NotBlank(message = "La url es obligatoria")
            String url,

            @NotEmpty(message = "La pizza necesita al menos un ingrediente")
            Set<@NotNull UUID> ingredientIds) {
    }

    public record Response(UUID id, BigDecimal price) {
    }
}
