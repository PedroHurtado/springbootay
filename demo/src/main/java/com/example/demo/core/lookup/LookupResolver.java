package com.example.demo.core.lookup;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Punto único para resolver FKs por tipo de entidad. Inyecta todos los
 * {@link Lookup} del contexto y los indexa por {@code type()}. Añadir un nuevo
 * destino de FK no obliga a tocar esta clase (Open/Closed).
 */
@Service
public class LookupResolver {

    private final Map<Class<?>, Lookup<?>> lookups;

    public LookupResolver(List<Lookup<?>> lookups) {
        this.lookups = lookups.stream().collect(toMap(Lookup::type, identity()));
    }

    @SuppressWarnings("unchecked")
    public <T> T find(Class<T> type, UUID id) {
        Lookup<T> lookup = (Lookup<T>) lookups.get(type);
        if (lookup == null) {
            throw new NoLookupRegisteredException(type);
        }
        return lookup.find(id);
    }

    public <T> Set<T> findAll(Class<T> type, Collection<UUID> ids) {
        return ids.stream()
                .map(id -> find(type, id))
                .collect(toSet());
    }
}
