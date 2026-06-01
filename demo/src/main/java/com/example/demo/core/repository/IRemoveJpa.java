package com.example.demo.core.repository;

import com.example.demo.core.domain.EntityBase;

/**
 * Implementa {@link IRemove#remove} como {@code default}. Extiende {@link IGetJpa}
 * para garantizar existencia (y lanzar si no) antes del borrado.
 */
public interface IRemoveJpa<T extends EntityBase, ID, J>
        extends IGetJpa<T, ID, J>, IRemove<T, ID> {

    @Override
    default void remove(ID id) {
        get(id);                    // lanza EntityNotFoundException si no existe
        jpa().deleteById(id);
    }
}
