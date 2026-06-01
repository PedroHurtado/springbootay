package com.example.demo.core.repository;

/** Acción de añadir. Añadir no requiere leer, por eso no extiende {@link IGet}. */
public interface IAdd<T> {
    void add(T entity);
}
