# Beans según el entorno: `@Profile` y `@ConditionalOn*`

A veces lo que cambia entre entornos no es un **valor** sino el **comportamiento**: qué implementación de un bean se carga. Para eso están `@Profile` y la familia `@ConditionalOn*`.

## `@Profile`: según el entorno

Caso típico: notificaciones. En desarrollo quieres un fake que solo loguea; en producción, el envío real.

La interfaz común no sabe nada de entornos:

```java
public interface NotificationSender {
    void send(String to, String message);
}
```

Implementación para desarrollo:

```java
@Component
@Profile("dev")
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(String to, String message) {
        log.info("[FAKE] Notificación a {}: {}", to, message);
    }
}
```

Implementación para stage y prod:

```java
@Component
@Profile({"stage", "prod"})
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    public EmailNotificationSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(String to, String message) {
        // envío real por correo
    }
}
```

El resto del código inyecta `NotificationSender` sin enterarse de cuál hay detrás. El contenedor elige según el perfil activo. Esto mantiene los handlers limpios y desacoplados del entorno.

## `@ConditionalOn*`: más fino que el entorno

Cuando la decisión no es "qué entorno" sino "está activada esta característica", `@ConditionalOn*` es más granular.

Bean por defecto solo si no hay otro ya definido (útil para defaults sobreescribibles):

```java
@Bean
@ConditionalOnMissingBean(NotificationSender.class)
public NotificationSender defaultSender() {
    return new LoggingNotificationSender();
}
```

Bean según el valor de una propiedad (feature flag):

```java
@Bean
@ConditionalOnProperty(name = "pizzeria.feature.split-payment", havingValue = "true")
public SplitPaymentService splitPaymentService() {
    return new SplitPaymentService();
}
```

## La distinción clave

- **`@Profile`** = "según el entorno" (dev / stage / prod).
- **`@ConditionalOnProperty`** = "según una feature flag concreta".

El segundo es más granular y no te obliga a meter todo en el concepto de entorno. Puedes tener una feature activada en producción para unos clientes y no para otros, sin crear un perfil nuevo para ello.

## Resumen

- `@Profile` carga un bean u otro según el perfil activo: ideal para fakes en dev vs implementaciones reales en prod.
- El código cliente inyecta la interfaz; el contenedor decide la implementación.
- `@ConditionalOnMissingBean` da defaults sobreescribibles.
- `@ConditionalOnProperty` activa beans por feature flag, más fino que el perfil.
- Regla mental: `@Profile` para entornos, `@ConditionalOnProperty` para características.
