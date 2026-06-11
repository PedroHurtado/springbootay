package com.example.demo.core.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import an.awesome.pipelinr.Notification;
import an.awesome.pipelinr.Pipeline;
import an.awesome.pipelinr.Pipelinr;

/**
 * Configura PipelinR como mediador de <strong>notificaciones</strong> (eventos de dominio).
 *
 * <p>Solo registramos los {@code Notification.Handler}: para los comandos este proyecto
 * mantiene su propio {@code CommandDispatcher}. PipelinR descubre cada handler porque es un
 * {@code @Component} que implementa {@link Notification.Handler}.</p>
 */
@Configuration
public class PipelinrConfig {

    @Bean
    Pipeline pipeline(@SuppressWarnings("rawtypes") ObjectProvider<Notification.Handler> notificationHandlers) {
        return new Pipelinr().with(() -> notificationHandlers.stream());
    }
}
