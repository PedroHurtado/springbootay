package com.example.demo.core.messaging;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.core.outbox.Outbox;
import com.example.demo.core.outbox.OutboxRepository;
import com.example.demo.core.outbox.OutboxStatus;

/**
 * Drena el Outbox hacia el {@link EventBus}. Es un proceso <strong>aparte</strong> de la peticion
 * que origino el evento: un {@code @Scheduled} que sondea la tabla en segundo plano.
 *
 * <p>Separar la escritura (transaccional, en la BD del servicio, hecha por el
 * {@link com.example.demo.core.outbox.DomainEventToOutboxHandler}) de la publicacion (en segundo
 * plano, reintentable) es la razon de ser del patron Outbox: si el broker esta caido, la operacion
 * de negocio no falla; el evento queda {@code PENDING} y se publica en la siguiente vuelta.</p>
 *
 * <p><strong>Garantia at-least-once.</strong> Si el proceso muere despues de {@code publish} pero
 * antes del commit, el evento se republica en la siguiente vuelta. El consumidor debe ser
 * idempotente.</p>
 */
@Component
public class MessageRelay {

    private final OutboxRepository outboxRepository;
    private final EventBus eventBus;

    public MessageRelay(OutboxRepository outboxRepository, EventBus eventBus) {
        this.outboxRepository = outboxRepository;
        this.eventBus = eventBus;
    }

    @Scheduled(fixedDelay = 5000)   // cada 5 s
    @Transactional
    public void drainOutbox() {
        // 1. Leer los pendientes en orden de emision (orden de insercion -> orden de publicacion).
        //    Como mucho 50 por vuelta: el relay drena en lotes, no toda la tabla de golpe.
        List<Outbox> pending = outboxRepository
                .findByStatusOrderByTimestampAsc(OutboxStatus.PENDING, Limit.of(50));

        if (pending.isEmpty()) {
            return;
        }

        // 2. Publicar cada uno y marcarlo como procesado.
        for (Outbox event : pending) {
            eventBus.publish(event);
            event.markAsProcessed();   // PENDING -> PROCESSED
            this.outboxRepository.save(event);
        }

        // 3. El commit de @Transactional persiste los cambios de status.
    }
}
