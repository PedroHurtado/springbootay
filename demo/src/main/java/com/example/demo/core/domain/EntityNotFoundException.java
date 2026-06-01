package com.example.demo.core.domain;

/**
 * Se lanza cuando se intenta leer/actualizar/borrar una entidad que no existe.
 * La emite {@code get(id)} de los repositorios (ver IGetJpa) en lugar de devolver
 * {@code Optional}, eliminando el ceremonial de {@code orElseThrow} en cada caller.
 */
public class EntityNotFoundException extends DomainException {

    private final transient Class<?> type;
    private final transient Object id;

    public EntityNotFoundException(Class<?> type, Object id) {
        super(type.getSimpleName() + " no encontrada: " + id);
        this.type = type;
        this.id = id;
    }

    public Class<?> getType() {
        return type;
    }

    public Object getEntityId() {
        return id;
    }
}
