package com.example.demo.core.repository;

import com.example.demo.core.domain.EntityBase;

/**
 * Implementa {@link IUpdate#update} como {@code default}. Extiende {@link IGetJpa}
 * para reutilizar su {@code get} (garantiza existencia antes de guardar).
 */
public interface IUpdateJpa<T extends EntityBase, ID, J>
        extends IGetJpa<T, ID, J>, IUpdate<T, ID> {

    @Override
    @SuppressWarnings("unchecked")
    default void update(T entity) {
        get((ID) entity.getId());   // lanza EntityNotFoundException si no existe
        save(entity);               // converge en RepositoryJpa#save: despacha eventos + persiste
    }
}
