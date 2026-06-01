package com.example.demo.core.repository;

/**
 * Acción de leer por id. {@code get} devuelve {@code T} o lanza
 * {@link com.example.demo.core.domain.EntityNotFoundException}; nunca {@code Optional}.
 */
public interface IGet<T, ID> {
    T get(ID id);
}
