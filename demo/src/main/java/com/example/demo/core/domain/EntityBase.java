package com.example.demo.core.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Base de toda entidad de dominio. Aporta identidad ({@code id}) e igualdad por id.
 * Una entidad NO emite eventos; eso es responsabilidad de la raíz del agregado
 * ({@link AggregateBase}).
 */
public abstract class EntityBase {

    private final UUID id;

    protected EntityBase(UUID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public UUID getId() {
        return id;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityBase that)) return false;
        return id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }
}
