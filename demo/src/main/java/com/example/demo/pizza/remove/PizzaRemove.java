package com.example.demo.pizza.remove;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.core.dispatcher.CommandDispatcher;
import com.example.demo.core.dispatcher.CommandHandler;
import com.example.demo.pizza.persistence.IRemovePizza;

/** Slice de borrado de Pizza. */
public class PizzaRemove {

    private PizzaRemove() {
    }

    @RestController
    @RequestMapping("/api/pizzas")
    public static class Endpoint {

        private final CommandDispatcher dispatcher;

        public Endpoint(CommandDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> remove(@PathVariable UUID id) {
            dispatcher.dispatch(new Command(id));
            return ResponseEntity.noContent().build();
        }
    }

    @Service
    public static class Handler implements CommandHandler<Command, Void> {

        private final IRemovePizza repository;

        public Handler(IRemovePizza repository) {
            this.repository = repository;
        }

        @Override
        public Class<Command> commandType() {
            return Command.class;
        }

        @Override
        public Void handle(Command command) {
            repository.remove(command.id());   // get() interno garantiza existencia
            return null;
        }
    }

    public record Command(UUID id) {
    }
}
