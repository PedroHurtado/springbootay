package com.example.demo.core.repository;

/** Acción de borrar. Extiende {@link IGet}: para borrar, primero hay que garantizar que existe. */
public interface IRemove<T, ID> extends IGet<T, ID> {
    void remove(ID id);
}
