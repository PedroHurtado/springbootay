package com.example.demo.core.domain;

import java.time.Instant;
import java.util.UUID;

import an.awesome.pipelinr.Notification;

/**
 * Evento de dominio: un hecho del pasado, inmutable, emitido por una raiz de agregado.
 *
 * <p>Lleva sellados el tipo y el id del agregado de origen ({@code domainType},
 * {@code domainId}). Esa informacion <strong>solo la conoce el agregado</strong> en el
 * momento de emitir, por eso la estampa {@link AggregateBase#add(String, Object)} y no el
 * consumidor.</p>
 *
 * <p>Implementa {@link Notification} para que PipelinR pueda despacharlo a los
 * {@code Notification.Handler} registrados. {@code Notification} es un marker interface
 * sin comportamiento; introduce una dependencia (consciente) de PipelinR en el dominio.</p>
 */
public record DomainEvent(
        String type,
        String domainType,
        UUID domainId,
        Instant occurredOn,
        Object payload) implements Notification {
}
