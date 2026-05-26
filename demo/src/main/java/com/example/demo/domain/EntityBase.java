package com.example.demo.domain;

import java.util.Objects;
import java.util.UUID;

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
