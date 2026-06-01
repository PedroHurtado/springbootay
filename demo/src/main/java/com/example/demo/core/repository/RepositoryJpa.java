package com.example.demo.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.core.domain.EntityBase;

/**
 * Base "sin herencia" de los repositorios JPA. Aporta los dos únicos puntos
 * variables que cada repositorio concreto debe cablear: el {@link JpaRepository}
 * interno y el {@link Mapper}.
 *
 * @param <T>  tipo de dominio (extiende {@link EntityBase} para poder extraer el id)
 * @param <ID> tipo del id
 * @param <J>  tipo de persistencia
 */
public interface RepositoryJpa<T extends EntityBase, ID, J> {
    JpaRepository<J, ID> jpa();
    Mapper<T, J> mapper();
}
