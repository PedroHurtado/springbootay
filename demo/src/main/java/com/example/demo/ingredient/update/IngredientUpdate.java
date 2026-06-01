package com.example.demo.ingredient.update;

import java.math.BigDecimal;
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
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.ingredient.persistence.IUpdateIngredient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Slice de actualización de Ingredient. */
public class IngredientUpdate {

    private IngredientUpdate() {
    }

    @RestController
    @RequestMapping("/api/ingredients")
    public static class Endpoint {

        private final CommandDispatcher dispatcher;

        public Endpoint(CommandDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @PutMapping("/{id}")
        public ResponseEntity<Response> update(@PathVariable UUID id, @Valid @RequestBody Body body) {
            Response response = dispatcher.dispatch(new Command(id, body.name(), body.cost()));
            return ResponseEntity.ok(response);
        }

        public record Body(
                @NotBlank(message = "El nombre es obligatorio")
                @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
                String name,

                @NotNull(message = "El coste es obligatorio")
                @PositiveOrZero(message = "El coste no puede ser negativo")
                BigDecimal cost) {
        }
    }

    @Service
    public static class Handler implements CommandHandler<Command, Response> {

        private final IUpdateIngredient repository;

        public Handler(IUpdateIngredient repository) {
            this.repository = repository;
        }

        @Override
        public Class<Command> commandType() {
            return Command.class;
        }

        @Override
        public Response handle(Command command) {
            Ingredient ingredient = repository.get(command.id());   // lanza si no existe
            ingredient.update(command.name(), command.cost());
            repository.update(ingredient);
            return new Response(ingredient.getId());
        }
    }

    public record Command(UUID id, String name, BigDecimal cost) {
    }

    public record Response(UUID id) {
    }
}
