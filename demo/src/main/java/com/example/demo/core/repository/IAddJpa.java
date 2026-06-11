package com.example.demo.core.repository;

import com.example.demo.core.domain.EntityBase;

/** Implementa {@link IAdd#add} como {@code default} componiendo {@link RepositoryJpa} + {@link IAdd}. */
public interface IAddJpa<T extends EntityBase, ID, J>
        extends RepositoryJpa<T, ID, J>, IAdd<T> {

    @Override
    default void add(T entity) {
        save(entity);   // converge en RepositoryJpa#save: despacha eventos + persiste
    }
}
