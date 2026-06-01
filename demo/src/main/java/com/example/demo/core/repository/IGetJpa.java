package com.example.demo.core.repository;

import com.example.demo.core.domain.EntityBase;
import com.example.demo.core.domain.EntityNotFoundException;

/** Implementa {@link IGet#get} como {@code default}: mapea a dominio o lanza si no existe. */
public interface IGetJpa<T extends EntityBase, ID, J>
        extends RepositoryJpa<T, ID, J>, IGet<T, ID> {

    /** Tipo de dominio, usado para construir la excepción cuando no se encuentra. */
    Class<T> domainType();

    @Override
    default T get(ID id) {
        return jpa().findById(id)
                .map(mapper()::toDomain)
                .orElseThrow(() -> new EntityNotFoundException(domainType(), id));
    }
}
