package com.example.demo.pizza.update;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.core.dispatcher.CommandDispatcher;
import com.example.demo.core.dispatcher.CommandHandler;
import com.example.demo.core.lookup.LookupResolver;
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.pizza.domain.Pizza;
import com.example.demo.pizza.persistence.IUpdatePizza;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Slice de actualización de Pizza. */
public class PizzaUpdate {

    private PizzaUpdate() {
    }

    @RestController
    @RequestMapping("/api/pizzas")
    public static class Endpoint {

        private final CommandDispatcher dispatcher;

        public Endpoint(CommandDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @PutMapping("/{id}")
        public ResponseEntity<Response> update(@PathVariable UUID id, @Valid @RequestBody Body body) {
            Command command = new Command(id, body.name(), body.description(), body.url(), body.ingredientIds());
            Response response = dispatcher.dispatch(command);
            return ResponseEntity.ok(response);
        }

        public record Body(
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
    }

    @Service
    public static class Handler implements CommandHandler<Command, Response> {

        private final IUpdatePizza repository;
        private final LookupResolver lookup;

        public Handler(IUpdatePizza repository, LookupResolver lookup) {
            this.repository = repository;
            this.lookup = lookup;
        }

        @Override
        public Class<Command> commandType() {
            return Command.class;
        }

        @Override
        public Response handle(Command command) {
            Pizza pizza = repository.get(command.id());   // lanza si no existe
            Set<Ingredient> ingredients = lookup.findAll(Ingredient.class, command.ingredientIds());

            pizza.update(command.name(), command.description(), command.url(), ingredients);

            repository.update(pizza);
            return new Response(pizza.getId(), pizza.getPrice());
        }
    }

    public record Command(
            UUID id,
            String name,
            String description,
            String url,
            Set<UUID> ingredientIds) {
    }

    public record Response(UUID id, BigDecimal price) {
    }
}
