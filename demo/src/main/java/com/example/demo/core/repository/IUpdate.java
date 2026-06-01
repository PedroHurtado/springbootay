package com.example.demo.core.repository;

/** Acción de actualizar. Extiende {@link IGet}: para actualizar, primero hay que leer. */
public interface IUpdate<T, ID> extends IGet<T, ID> {
    void update(T entity);
}
