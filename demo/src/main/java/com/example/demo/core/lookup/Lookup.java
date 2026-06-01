package com.example.demo.core.lookup;

import java.util.UUID;

/**
 * Resuelve una referencia (FK) a una entidad de dominio por id. Cada agregado que
 * sea destino de FK registra un {@code Lookup<T>} como {@code @Component}; el
 * {@link LookupResolver} los recoge por inyección de colección.
 */
public interface Lookup<T> {
    Class<T> type();
    T find(UUID id);   // lanza EntityNotFoundException si no existe
}
