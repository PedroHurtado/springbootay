package com.example.demo.core.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.demo.core.outbox.Outbox;

/**
 * Implementacion de {@link EventBus} que "publica" escribiendo por consola.
 *
 * <p>Sustituye al {@code RabbitMqEventBus} de la leccion: en el curso no levantamos un broker.
 * Lo importante del patron Outbox no es <em>contra que</em> se publica, sino <em>cuando</em> y
 * <em>como</em> (un proceso aparte, reintentable, que drena la tabla). Cambiar este bean por uno
 * de RabbitMQ no tocaria ni el {@link MessageRelay} ni el resto del flujo.</p>
 */
@Component
public class ConsoleEventBus implements EventBus {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEventBus.class);

    @Override
    public void publish(Outbox event) {
        log.info("[EventBus] publicando evento {} de {}({}) -> payload={}",
                event.getEventType(), event.getAggregate(), event.getAggregateId(), event.getPayload());
    }
}
