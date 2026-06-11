package com.example.demo.core.outbox;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio del Outbox. Spring Data implementa el CRUD; añadimos la consulta que el
 * relay necesita para drenar los pendientes en orden de emision.
 */
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    List<Outbox> findByStatusOrderByTimestampAsc(OutboxStatus status);
}
