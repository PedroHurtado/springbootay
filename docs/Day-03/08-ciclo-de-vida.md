# Ciclo de vida de los beans

## Las fases de un bean

El contenedor gestiona cada bean a lo largo de varias fases. Conocerlas permite ejecutar código en los momentos adecuados: inicializar recursos al arrancar, liberar conexiones al parar, validar configuración antes de servir peticiones, etc.

Las fases principales son:

1. **Instanciación**: el contenedor llama al constructor.
2. **Inyección de dependencias**: se asignan setters y campos (si se usan).
3. **Aware callbacks**: si el bean implementa interfaces `Aware` (`BeanNameAware`, `ApplicationContextAware`...), se le pasan los valores correspondientes.
4. **BeanPostProcessor `before`**: procesadores registrados pueden modificar la instancia.
5. **Inicialización**: callbacks de inicialización del bean.
6. **BeanPostProcessor `after`**: procesadores aplican proxies u otra lógica final.
7. **El bean está listo y operativo**.
8. **Destrucción** (al cerrar el contexto): callbacks de destrucción.

Para código de aplicación normal, las fases que importan son **inicialización** y **destrucción**.

## @PostConstruct

`@PostConstruct` marca un método que se ejecuta **después** de que el bean haya sido construido y sus dependencias inyectadas.

```java
@Service
public class IngredientCache {

    private final IngredientRepository repository;
    private Map<String, Ingredient> cache;

    public IngredientCache(IngredientRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void warmUp() {
        this.cache = repository.findAll().stream()
            .collect(toMap(Ingredient::id, identity()));
    }
}
```

Cuando el contenedor termine de construir `IngredientCache` e inyectar sus dependencias, llamará a `warmUp()` automáticamente. La clase ya está totalmente operativa: puede usar todas sus dependencias.

**Casos de uso típicos**:

- Precargar cachés.
- Validar configuración crítica.
- Abrir conexiones a recursos.
- Registrar el bean en sistemas externos (event bus, scheduler...).

`@PostConstruct` pertenece al paquete `jakarta.annotation` (anteriormente `javax.annotation`). Es una anotación estándar de Jakarta EE, no específica de Spring.

## @PreDestroy

`@PreDestroy` marca un método que se ejecuta **antes** de que el contenedor destruya el bean, normalmente cuando la aplicación se está cerrando.

```java
@Service
public class MessageBroker {

    private Connection connection;

    @PostConstruct
    public void connect() {
        this.connection = openConnection();
    }

    @PreDestroy
    public void disconnect() {
        if (connection != null) {
            connection.close();
        }
    }
}
```

**Casos de uso típicos**:

- Cerrar conexiones a bases de datos, message brokers, colas.
- Volcar buffers pendientes.
- Desregistrar listeners.
- Liberar recursos del sistema operativo.

**Importante**: `@PreDestroy` solo se ejecuta para **beans singleton**. En scope `prototype`, el contenedor no gestiona la destrucción y este callback **nunca se llama**.

## InitializingBean y DisposableBean

Spring ofrece dos interfaces equivalentes a las anotaciones anteriores:

```java
public class IngredientCache implements InitializingBean, DisposableBean {

    @Override
    public void afterPropertiesSet() throws Exception {
        // equivalente a @PostConstruct
    }

    @Override
    public void destroy() throws Exception {
        // equivalente a @PreDestroy
    }
}
```

**No usar.** Acoplan la clase a Spring. `@PostConstruct` y `@PreDestroy` son anotaciones estándar y no requieren implementar interfaces específicas del framework.

Las interfaces siguen existiendo por compatibilidad histórica y porque la propia infraestructura interna de Spring las usa.

## initMethod y destroyMethod en @Bean

Cuando declaramos un bean con `@Bean` de una clase de terceros (sobre la que no podemos poner `@PostConstruct`), podemos indicar los métodos de init y destroy explícitamente:

```java
@Configuration
public class CacheConfig {

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public ExpensiveResource resource() {
        return new ExpensiveResource();
    }
}
```

Spring llamará a `start()` después de construir el bean y a `shutdown()` antes de destruirlo.

Para muchas clases que implementan `AutoCloseable` o `Closeable`, Spring detecta automáticamente el método `close()` como destrucción sin necesidad de indicarlo. Si quieres desactivar este comportamiento, usa `destroyMethod = ""`.

## Orden de inicialización entre beans

El contenedor inicializa los beans en orden de dependencias: si A depende de B, B se construye y se inicializa antes que A. Esto se deduce automáticamente del grafo de dependencias.

Cuando dos beans no tienen relación de dependencia pero el orden importa (por ejemplo, dos componentes que se registran en un mismo recurso externo), se puede forzar:

```java
@Service
@DependsOn("metricsRegistry")
public class IngredientService { ... }
```

`@DependsOn` indica que el bean nombrado debe inicializarse antes que este. Se usa raramente — la mayoría de las veces, declarar la dependencia real en el constructor es la forma natural y limpia de establecer el orden.

## Beans lazy

Por defecto, Spring **inicializa todos los beans singleton al arrancar**. Es el comportamiento eager. Esto es bueno: si algo falla, falla en arranque, no en runtime al recibir la primera petición.

A veces queremos posponer la inicialización de un bean concreto:

```java
@Service
@Lazy
public class HeavyReportGenerator { ... }
```

El bean no se construye hasta que alguien lo solicita por primera vez. Útil cuando:

- La inicialización es muy costosa y el bean solo se usa en algunos casos.
- El bean tiene una dependencia que solo está disponible bajo ciertas condiciones de runtime.

En consumidores también:

```java
public IngredientService(@Lazy HeavyReportGenerator generator) { ... }
```

Aquí Spring inyecta un proxy. El bean real se construye en la primera llamada a un método.

`@Lazy` debe usarse con criterio. Hacer todo lazy convierte fallos de configuración en fallos en producción que aparecen en horas raras.

## @PostConstruct y operaciones largas

`@PostConstruct` se ejecuta **dentro del arranque del contenedor**. Si bloquea, el arranque se bloquea. Para tareas largas (precargas grandes, conexiones lentas, llamadas remotas), evalúa hacerlas en segundo plano o vincularlas a un evento posterior al arranque:

```java
@Service
public class IngredientCache {

    @EventListener(ApplicationReadyEvent.class)
    public void warmUpAfterStartup() {
        // se ejecuta cuando la aplicación ya está lista
    }
}
```

`ApplicationReadyEvent` se publica al final del arranque, cuando todos los beans están construidos y el embedded server está escuchando. Es el momento ideal para tareas que no deben bloquear el arranque pero quieren ejecutarse "una vez al iniciar".

## Resumen

- `@PostConstruct`: método que se ejecuta tras la construcción e inyección de dependencias.
- `@PreDestroy`: método que se ejecuta antes de destruir el bean (solo singletons).
- Ambas son anotaciones estándar de Jakarta — preferibles a `InitializingBean` / `DisposableBean`.
- Para beans declarados con `@Bean` se pueden indicar `initMethod` y `destroyMethod`.
- El orden de inicialización se deduce del grafo de dependencias; `@DependsOn` se usa solo cuando no hay dependencia real.
- `@Lazy` pospone la creación del bean. Útil con criterio.
- Para tareas pesadas al arrancar, considera `ApplicationReadyEvent` en lugar de `@PostConstruct`.
