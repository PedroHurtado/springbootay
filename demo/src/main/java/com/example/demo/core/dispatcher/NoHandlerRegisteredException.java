package com.example.demo.core.dispatcher;

/** Se lanza cuando no hay handler registrado para el tipo de comando o consulta. */
public class NoHandlerRegisteredException extends RuntimeException {

    public NoHandlerRegisteredException(Class<?> messageType) {
        super("No hay handler registrado para " + messageType.getName());
    }
}
