package com.example.demo.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Raíz de agregado. Además de identidad, acumula eventos de dominio.
 * Solo las raíces de agregado emiten eventos.
 */
public abstract class AggregateBase extends EntityBase {

    private final List<DomainEvent> events = new ArrayList<>();

    protected AggregateBase(UUID id) {
        super(id);
    }

    /**
     * Emite un evento de dominio. El agregado sella {@code domainType} (su clase) y
     * {@code domainId} (su id): es el unico que los conoce en este punto.
     *
     * @param type    tipo del evento, p. ej. {@code "pizza:create"}
     * @param payload datos del evento (puede ser el propio agregado)
     */
    protected void add(String type, Object payload) {
        events.add(new DomainEvent(type, getClass().getSimpleName(), getId(), Instant.now(), payload));
    }

    public List<DomainEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void clearEvents() {
        events.clear();
    }
}
