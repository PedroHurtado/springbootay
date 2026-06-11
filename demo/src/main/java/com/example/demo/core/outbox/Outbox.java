package com.example.demo.core.outbox;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * Fila del patron Outbox. Es la <strong>persistencia</strong> de un evento de dominio que
 * debe salir del servicio (Camino 1): se escribe en la misma transaccion que el cambio de
 * estado del agregado y, mas tarde, un proceso aparte la drena hacia el broker.
 *
 * <p>El {@code payload} se guarda serializado a JSON. En H2 se mapea como CLOB ({@code @Lob});
 * en PostgreSQL seria {@code jsonb}.</p>
 */
@Entity
@Table(name = "outbox")
public class Outbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /** Tipo del agregado de origen (p. ej. {@code "Pizza"}). */
    @Column(name = "aggregate", nullable = false)
    private String aggregate;

    /** Id del agregado de origen. */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "username")
    private String user;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OutboxStatus status;

    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    /** Correlacion entre servicios (opcional). */
    @Column(name = "trace_id")
    private String traceId;

    protected Outbox() {
        // requerido por JPA
    }

    public Outbox(String eventType, Instant timestamp, String aggregate, UUID aggregateId,
                  String user, String payload, String traceId) {
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.aggregate = aggregate;
        this.aggregateId = aggregateId;
        this.user = user;
        this.status = OutboxStatus.PENDING;
        this.payload = payload;
        this.traceId = traceId;
    }

    /** PENDING -> PROCESSED. Lo invoca el relay tras publicar al broker. */
    public void markAsProcessed() {
        this.status = OutboxStatus.PROCESSED;
    }

    public Long getId()            { return id; }
    public String getEventType()   { return eventType; }
    public Instant getTimestamp()  { return timestamp; }
    public String getAggregate()   { return aggregate; }
    public UUID getAggregateId()   { return aggregateId; }
    public String getUser()        { return user; }
    public OutboxStatus getStatus() { return status; }
    public String getPayload()     { return payload; }
    public String getTraceId()     { return traceId; }
}
