package com.example.demo.core.lookup;

/** Se lanza cuando se pide resolver un tipo para el que no hay {@link Lookup} registrado. */
public class NoLookupRegisteredException extends RuntimeException {

    public NoLookupRegisteredException(Class<?> type) {
        super("No hay Lookup registrado para el tipo " + type.getName());
    }
}
