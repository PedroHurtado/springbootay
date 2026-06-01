package com.example.demo.core.dispatcher;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * Enruta cada comando a su {@link CommandHandler}. Inyecta todos los handlers del
 * contexto y los indexa por {@code commandType()}. Un nuevo slice de escritura se
 * registra solo con declarar su handler como bean; el dispatcher lo recoge.
 */
@Service
public class CommandDispatcher {

    private final Map<Class<?>, CommandHandler<?, ?>> handlers;

    public CommandDispatcher(List<CommandHandler<?, ?>> handlers) {
        this.handlers = handlers.stream().collect(toMap(CommandHandler::commandType, identity()));
    }

    @SuppressWarnings("unchecked")
    public <C, R> R dispatch(C command) {
        CommandHandler<C, R> handler = (CommandHandler<C, R>) handlers.get(command.getClass());
        if (handler == null) {
            throw new NoHandlerRegisteredException(command.getClass());
        }
        return handler.handle(command);
    }
}
