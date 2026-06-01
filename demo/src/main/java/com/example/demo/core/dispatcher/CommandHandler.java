package com.example.demo.core.dispatcher;

/**
 * Handler de un caso de uso de escritura. Declara el tipo de comando que atiende
 * para que el {@link CommandDispatcher} lo enrute.
 *
 * @param <C> tipo del comando
 * @param <R> tipo de la respuesta
 */
public interface CommandHandler<C, R> {
    Class<C> commandType();
    R handle(C command);
}
