package com.example.demo.ingredient.create;

import java.math.BigDecimal;
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
import com.example.demo.ingredient.domain.Ingredient;
import com.example.demo.ingredient.persistence.IAddIngredient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Slice de creación de Ingredient. */
public class IngredientCreate {

    private IngredientCreate() {
    }

    @RestController
    @RequestMapping("/api/ingredients")
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

        private final IAddIngredient repository;

        public Handler(IAddIngredient repository) {
            this.repository = repository;
        }

        @Override
        public Class<Command> commandType() {
            return Command.class;
        }

        @Override
        public Response handle(Command command) {
            Ingredient ingredient = Ingredient.create(
                    UUID.randomUUID(), command.name(), command.cost());
            repository.add(ingredient);
            return new Response(ingredient.getId());
        }
    }

    public record Command(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
            String name,

            @NotNull(message = "El coste es obligatorio")
            @PositiveOrZero(message = "El coste no puede ser negativo")
            BigDecimal cost) {
    }

    public record Response(UUID id) {
    }
}
