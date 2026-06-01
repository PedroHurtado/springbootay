package com.example.demo.core.dispatcher;

/**
 * Handler de un caso de uso de lectura. Análogo a {@link CommandHandler} pero para
 * consultas; lo enruta el {@link QueryDispatcher}.
 *
 * @param <Q> tipo de la consulta
 * @param <R> tipo del resultado
 */
public interface QueryHandler<Q, R> {
    Class<Q> queryType();
    R handle(Q query);
}
