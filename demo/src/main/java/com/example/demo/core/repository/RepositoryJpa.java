package com.example.demo.core.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import an.awesome.pipelinr.Pipeline;

import com.example.demo.core.domain.AggregateBase;
import com.example.demo.core.domain.DomainEvent;
import com.example.demo.core.domain.EntityBase;

/**
 * Base "sin herencia" de los repositorios JPA. Aporta los puntos variables que cada
 * repositorio concreto debe cablear: el {@link JpaRepository} interno, el {@link Mapper}
 * y el {@link Pipeline} de PipelinR (para despachar eventos de dominio).
 *
 * <p>Define ademas el <strong>unico punto de escritura</strong> ({@link #save(EntityBase)}):
 * el split {@code add}/{@code update}/{@code remove} de las interfaces {@code *Jpa} es ISP
 * aplicado al repositorio, pero todos los caminos de escritura convergen aqui. Por eso el
 * despacho de eventos vive en este metodo y no repartido en cada uno.</p>
 *
 * @param <T>  tipo de dominio (extiende {@link EntityBase} para poder extraer el id)
 * @param <ID> tipo del id
 * @param <J>  tipo de persistencia
 */
public interface RepositoryJpa<T extends EntityBase, ID, J> {
    JpaRepository<J, ID> jpa();
    Mapper<T, J> mapper();
    Pipeline pipeline();

    /**
     * Punto de convergencia de la escritura: despacha los eventos de dominio del agregado
     * (si los tiene) <strong>antes de mapear</strong> y delega en JPA. Como se ejecuta
     * dentro de la transaccion del caso de uso, lo que hagan los handlers (p. ej. escribir
     * en el Outbox) y el {@code save} se confirman en un <strong>unico commit</strong>.
     */
    default J save(T entity) {
        dispatchDomainEvents(entity);
        return jpa().save(mapper().toJpa(entity));
    }

    /**
     * Solo las raices de agregado emiten eventos; una {@link EntityBase} normal no entra
     * por aqui. Materializa con copia defensiva y limpia <em>antes</em> de publicar:
     * si un handler vuelve a tocar el agregado, no se reprocesan los mismos eventos.
     */
    private void dispatchDomainEvents(T entity) {
        if (entity instanceof AggregateBase aggregate) {
            List<DomainEvent> events = List.copyOf(aggregate.getEvents());
            aggregate.clearEvents();
            events.forEach(pipeline()::send);
        }
    }
}
