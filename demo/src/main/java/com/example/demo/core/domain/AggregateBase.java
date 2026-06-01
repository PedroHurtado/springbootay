package com.example.demo.core.domain;

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

    protected void add(String name, Object payload) {
        events.add(new DomainEvent(name, payload));
    }

    public List<DomainEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void clearEvents() {
        events.clear();
    }
}
