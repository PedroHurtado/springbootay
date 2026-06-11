package com.example.demo.core.outbox;

/** Estado de una fila del Outbox en su camino hacia el broker. */
public enum OutboxStatus {
    PENDING,
    PROCESSED
}
