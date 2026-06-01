package com.example.demo.core.repository;

/**
 * Contrato común de los mappers dominio &harr; JPA. Permite que las interfaces base
 * ({@link RepositoryJpa} y derivadas) mapeen genéricamente sin conocer el tipo concreto.
 *
 * @param <T> tipo de dominio
 * @param <J> tipo de persistencia (entidad JPA)
 */
public interface Mapper<T, J> {
    J toJpa(T domain);
    T toDomain(J jpa);
}
