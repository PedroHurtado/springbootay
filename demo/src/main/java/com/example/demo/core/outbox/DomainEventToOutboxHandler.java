package com.example.demo.core.outbox;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import an.awesome.pipelinr.Notification;

import com.example.demo.core.domain.DomainEvent;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Camino 1: handler que persiste todo {@link DomainEvent} en el Outbox.
 *
 * <p>PipelinR lo invoca dentro del despacho del {@code save} del repositorio, es decir,
 * dentro de la transaccion del caso de uso. No hay commit aqui: lo hara el cierre de la
 * transaccion (la del {@code @Transactional} del handler de comando). Asi, el cambio de
 * estado del agregado y esta fila del Outbox se confirman juntos o no se confirma ninguno.</p>
 *
 * <p>Es un handler <em>generico</em>: como en este proyecto todos los eventos comparten el
 * tipo {@link DomainEvent}, PipelinR enruta todos aqui. El {@code eventType} (p. ej.
 * {@code "pizza:create"}) distingue cada uno dentro de la fila.</p>
 */
@Component
public class DomainEventToOutboxHandler implements Notification.Handler<DomainEvent> {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DomainEventToOutboxHandler(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(DomainEvent event) {
        Outbox row = new Outbox(
                event.type(),
                event.occurredOn(),
                event.domainType(),
                event.domainId(),
                currentUser(),
                toJson(event.payload()),
                null);          // trace_id: correlacion entre servicios, pendiente (Modulo 6)
        outboxRepository.save(row);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("No se pudo serializar el payload del evento", e);
        }
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }
}
