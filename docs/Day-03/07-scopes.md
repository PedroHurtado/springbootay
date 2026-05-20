# Scopes de beans

## Qué es el scope

El **scope** define cuántas instancias del bean crea el contenedor y cuánto tiempo viven. Por defecto, Spring crea **un único objeto por bean** (scope `singleton`), pero hay otros scopes para casos específicos.

## Los scopes disponibles

Spring ofrece varios scopes. Los más usados:

| Scope | Cuántas instancias | Ciclo de vida |
|---|---|---|
| `singleton` | Una por contenedor (por defecto) | Vive mientras viva el contenedor |
| `prototype` | Una nueva cada vez que se solicita | El contenedor no la gestiona después de entregarla |
| `request` | Una por petición HTTP | Mientras dura la petición |
| `session` | Una por sesión HTTP | Mientras dura la sesión |
| `application` | Una por `ServletContext` | Vida del servlet |
| `websocket` | Una por sesión WebSocket | Mientras dura la sesión WebSocket |

Los tres últimos solo aplican en aplicaciones web.

## Singleton (el comportamiento por defecto)

Una única instancia, cacheada por el contenedor, compartida por todos los que la inyecten.

```java
@Service
public class IngredientService { ... }
```

Todos los consumidores reciben el **mismo objeto**. Esto implica que **el bean debe ser stateless** (o gestionar su estado con cuidado para concurrencia): múltiples hilos pueden usarlo simultáneamente.

Es el scope por defecto porque para la mayoría de servicios y repositorios es lo que queremos: objetos sin estado, reutilizables.

## Prototype

Cada vez que alguien pide el bean, el contenedor crea una instancia nueva.

```java
@Service
@Scope("prototype")
public class ReservationDraft { ... }
```

Características importantes:

- El contenedor **construye** el bean y lo entrega, pero **no gestiona su destrucción**. Los callbacks `@PreDestroy` no se ejecutan. El consumidor es responsable de su ciclo de vida.
- Cada inyección de un prototype en un singleton recibe una instancia **en el momento de construir el singleton**, no en cada uso.

Esta última característica es la fuente del problema más típico relacionado con scopes.

## El problema prototype-en-singleton

Imagina un singleton que necesita una instancia fresca de un prototype en cada operación:

```java
@Service
public class ReservationService {

    private final ReservationDraft draft;   // ← se inyecta UNA VEZ

    public ReservationService(ReservationDraft draft) {
        this.draft = draft;
    }

    public void createReservation() {
        draft.reset();
        // ... usa draft ...
    }
}
```

`ReservationService` es singleton. Spring lo construye una vez, y al hacerlo le inyecta **una** instancia de `ReservationDraft`. Aunque `ReservationDraft` está declarado como prototype, el singleton solo recibe esa primera instancia y la reutiliza para siempre.

El scope `prototype` **no funciona como uno espera** cuando se inyecta directamente en un singleton. Hay tres formas correctas de resolverlo.

### Opción 1: ObjectProvider (recomendada)

`ObjectProvider<T>` es una factoría que el contenedor inyecta. Cada vez que llamamos a `getObject()`, el contenedor crea una instancia nueva.

```java
@Service
public class ReservationService {

    private final ObjectProvider<ReservationDraft> draftProvider;

    public ReservationService(ObjectProvider<ReservationDraft> draftProvider) {
        this.draftProvider = draftProvider;
    }

    public void createReservation() {
        ReservationDraft draft = draftProvider.getObject();
        // ... usa draft ...
    }
}
```

Es la forma idiomática moderna. Type-safe, no acopla a Spring (`ObjectProvider` es de Spring, pero la alternativa `Provider<T>` de `jakarta.inject` funciona igual y es estándar).

### Opción 2: Scoped proxy

Marcamos el prototype con un proxy:

```java
@Service
@Scope(value = "prototype", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ReservationDraft { ... }
```

Ahora, lo que se inyecta en el singleton **no es el prototype directamente, sino un proxy**. Cada llamada a un método del proxy delega en una nueva instancia del prototype.

```java
@Service
public class ReservationService {
    private final ReservationDraft draft;   // ← es un proxy

    public ReservationService(ReservationDraft draft) {
        this.draft = draft;
    }

    public void createReservation() {
        draft.reset();   // ← cada llamada usa una instancia nueva
    }
}
```

Funciona pero tiene una pega: cada llamada a un método del proxy crea una instancia nueva. Si dentro de `createReservation()` llamamos a `draft.method1()` y luego a `draft.method2()`, son **dos instancias distintas**. Solo es viable si el prototype es realmente "sin estado entre llamadas", lo que rara vez es lo que queremos.

### Opción 3: Lookup method injection

Spring puede sobrescribir un método abstracto para que devuelva un bean fresco:

```java
@Service
public abstract class ReservationService {

    public void createReservation() {
        ReservationDraft draft = createDraft();
        // ... usa draft ...
    }

    @Lookup
    protected abstract ReservationDraft createDraft();
}
```

Funciona, pero es la opción menos legible y menos usada. `ObjectProvider` la ha reemplazado en código moderno.

## Scopes web

### request

Una instancia por petición HTTP. Útil para guardar contexto de la petición actual sin pasarlo como parámetro.

```java
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    private String userId;
    // ...
}
```

Necesita `proxyMode` para poder inyectarse en singletons (mismo motivo que prototype).

### session

Una instancia por sesión HTTP. Para datos por usuario en aplicaciones con estado de sesión.

```java
@Component
@Scope(value = "session", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class UserCart { ... }
```

En aplicaciones modernas REST sin sesión, no se usa.

### application

Una instancia por `ServletContext`. Funcionalmente similar a singleton en la mayoría de aplicaciones web, pero compartida también con servlets no-Spring.

## Cuándo usar cada scope

**Singleton (por defecto)**: prácticamente todo. Servicios, repositorios, controladores, mappers, validadores.

**Prototype**: cuando el bean tiene estado que no se puede compartir entre operaciones. Casos válidos pero infrecuentes; muchas veces lo que queremos es un objeto plano (POJO/record) construido manualmente, no un bean prototype.

**Request**: para datos de contexto de petición. Útil con interceptores que rellenan el contexto al inicio (usuario autenticado, locale, request ID...).

**Session/Application/WebSocket**: casos muy específicos.

## La pregunta antes del scope

Antes de declarar un scope distinto de singleton, conviene preguntarse: **¿realmente necesito que el contenedor gestione este objeto?**

Muchas veces lo que parece pedir un prototype es en realidad un objeto que deberíamos construir nosotros con `new` y pasar como parámetro. El contenedor de Spring está pensado para componentes con dependencias inyectadas, no para portadores de datos. Si el objeto tiene estado y vida corta, suele ser mejor un record o POJO normal.

## Resumen

- `singleton` es el scope por defecto: una instancia compartida.
- `prototype`: una instancia nueva por solicitud al contenedor.
- Inyectar un prototype en un singleton es un error frecuente: se inyecta una sola vez. Soluciones: `ObjectProvider`, scoped proxy, o `@Lookup`.
- `ObjectProvider` es la opción moderna y limpia.
- Scopes web (`request`, `session`...) para datos por petición o sesión.
- Antes de usar prototype, considera si el objeto debería ser simplemente un POJO/record y no un bean.
