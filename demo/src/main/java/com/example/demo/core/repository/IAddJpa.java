package com.example.demo.core.repository;

import com.example.demo.core.domain.EntityBase;

/** Implementa {@link IAdd#add} como {@code default} componiendo {@link RepositoryJpa} + {@link IAdd}. */
public interface IAddJpa<T extends EntityBase, ID, J>
        extends RepositoryJpa<T, ID, J>, IAdd<T> {

    @Override
    default void add(T entity) {
        jpa().save(mapper().toJpa(entity));
    }
}
