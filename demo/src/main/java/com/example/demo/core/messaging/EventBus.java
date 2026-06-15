package com.example.demo.core.messaging;

import com.example.demo.core.outbox.Outbox;

/**
 * Abstraccion del broker de mensajeria. El {@link com.example.demo.core.messaging.MessageRelay}
 * depende de este contrato, no de una tecnologia concreta.
 *
 * <p>En produccion su implementacion seria RabbitMQ (Spring AMQP con {@code RabbitTemplate}).
 * En el curso usamos {@link ConsoleEventBus}, que escribe por consola: demuestra que el relay
 * publica contra una abstraccion y que cambiar el destino (consola, RabbitMQ, Kafka...) es solo
 * cambiar la implementacion de este bean.</p>
 */
public interface EventBus {

    void publish(Outbox event);
}
