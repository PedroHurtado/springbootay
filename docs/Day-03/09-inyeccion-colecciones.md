# Inyección de colecciones: List<T> y Map<String, T>

## El mecanismo

Spring permite inyectar **todas las implementaciones de una interfaz** como una colección. Es uno de los mecanismos más potentes del contenedor y la base de muchos patrones de extensibilidad.

```java
public interface PaymentProvider {
    String getId();
    PaymentResult charge(PaymentRequest request);
}

@Service
public class StripeProvider implements PaymentProvider {
    public String getId() { return "stripe"; }
    // ...
}

@Service
public class PaylandsProvider implements PaymentProvider {
    public String getId() { return "paylands"; }
    // ...
}
```

Ahora podemos inyectar **todas** las implementaciones:

```java
@Service
public class PaymentService {

    private final List<PaymentProvider> providers;

    public PaymentService(List<PaymentProvider> providers) {
        this.providers = providers;
    }
}
```

Spring detecta que `List<PaymentProvider>` solicita "todos los beans de tipo `PaymentProvider`" y inyecta la lista completa.

## Inyección como Map

Aún más útil: inyectar como `Map<String, T>`. La clave es el **nombre del bean** y el valor es la instancia.

```java
@Service
public class PaymentService {

    private final Map<String, PaymentProvider> providers;

    public PaymentService(Map<String, PaymentProvider> providers) {
        this.providers = providers;
    }

    public PaymentResult charge(String providerId, PaymentRequest request) {
        PaymentProvider provider = providers.get(providerId);
        return provider.charge(request);
    }
}
```

El map quedaría:

```
"stripeProvider"   → StripeProvider instance
"paylandsProvider" → PaylandsProvider instance
```

Si quieres claves más limpias (`"stripe"`, `"paylands"`), nombra los beans explícitamente:

```java
@Service("stripe")
public class StripeProvider implements PaymentProvider { ... }

@Service("paylands")
public class PaylandsProvider implements PaymentProvider { ... }
```

## El patrón "resolver por id de runtime"

Cuando la elección de implementación depende de datos en runtime (no de configuración), el patrón natural es **construir el map por una clave del propio bean**, no por el nombre del bean:

```java
@Service
public class PaymentProviderResolver {

    private final Map<String, PaymentProvider> providersById;

    public PaymentProviderResolver(List<PaymentProvider> providers) {
        this.providersById = providers.stream()
            .collect(toMap(PaymentProvider::getId, identity()));
    }

    public PaymentProvider resolve(String providerId) {
        PaymentProvider provider = providersById.get(providerId);
        if (provider == null) {
            throw new UnknownPaymentProviderException(providerId);
        }
        return provider;
    }
}
```

Ventajas frente a usar el map de Spring directamente:

- La clave es **explícita** y vive en el dominio (un id semántico, no el nombre Java del bean).
- No depende de cómo se nombran los beans, solo de su implementación.
- Refactor-safe: renombrar la clase no rompe nada.

Este es el patrón habitual para sistemas con múltiples proveedores, estrategias o handlers.

## Aplicado a Vertical Slice con handlers

Imagina que tenemos varios slices con su `Handler` y un dispatcher central que los enruta:

```java
public interface CommandHandler<C, R> {
    Class<C> commandType();
    R handle(C command);
}

public class IngredientCreate {

    @RestController
    @RequestMapping("/api/ingredients")
    public static class Endpoint {
        private final CommandDispatcher dispatcher;

        public Endpoint(CommandDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @PostMapping
        public Response create(@RequestBody Command command) {
            return dispatcher.dispatch(command);
        }
    }

    @Service
    public static class Handler implements CommandHandler<Command, Response> {
        public Class<Command> commandType() { return Command.class; }
        public Response handle(Command command) { ... }
    }

    public record Command() { }
    public record Response() { }
}
```

Y el dispatcher inyecta todos los handlers:

```java
@Service
public class CommandDispatcher {

    private final Map<Class<?>, CommandHandler<?, ?>> handlers;

    public CommandDispatcher(List<CommandHandler<?, ?>> handlers) {
        this.handlers = handlers.stream()
            .collect(toMap(CommandHandler::commandType, identity()));
    }

    @SuppressWarnings("unchecked")
    public <C, R> R dispatch(C command) {
        CommandHandler<C, R> handler = (CommandHandler<C, R>) handlers.get(command.getClass());
        if (handler == null) {
            throw new NoHandlerForCommandException(command.getClass());
        }
        return handler.handle(command);
    }
}
```

Cada slice nuevo aporta su handler. El dispatcher no necesita conocerlos individualmente: los recoge todos vía inyección de colección. Es **el patrón de extensibilidad por excelencia** en Spring.

## @Order: controlar el orden

Cuando se inyecta una `List<T>`, Spring respeta el orden definido por `@Order` (o por la interfaz `Ordered`).

```java
@Component
@Order(1)
public class AuthenticationFilter implements RequestFilter { ... }

@Component
@Order(2)
public class RateLimitingFilter implements RequestFilter { ... }

@Component
@Order(3)
public class LoggingFilter implements RequestFilter { ... }
```

Al inyectar `List<RequestFilter>`, Spring entrega la lista en orden de `@Order` (menor primero). Útil para cadenas de filtros, validadores o interceptores.

`@Order` no afecta a `Map<String, T>` — los maps no tienen orden conceptual.

## Lista vacía vs error

Si no hay ningún bean del tipo, la inyección de colección recibe una **lista o map vacíos**, no falla. Esto es distinto del comportamiento normal de inyección (donde la ausencia de bean es un error).

```java
public PaymentService(List<PaymentProvider> providers) {
    // si no hay providers, providers.size() == 0
}
```

Es coherente con la semántica: "dame todos los que haya, incluso si son cero".

## Inyección de Set y Collection

Además de `List` y `Map`, Spring admite `Set<T>` y `Collection<T>`. `List` es lo habitual; `Set` es útil cuando quieres garantizar unicidad pero raramente lo necesitarás.

## Filtrado con qualifiers

Se puede inyectar **solo un subconjunto** de los beans usando qualifiers:

```java
@Target({ ElementType.TYPE, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface External { }

@Service
@External
public class StripeProvider implements PaymentProvider { ... }

@Service
@External
public class PaylandsProvider implements PaymentProvider { ... }

@Service
public class InternalSettlementProvider implements PaymentProvider { ... }

@Service
public class ExternalPaymentRouter {
    public ExternalPaymentRouter(@External List<PaymentProvider> providers) {
        // recibe solo Stripe y Paylands, no InternalSettlement
    }
}
```

Patrón útil para distinguir grupos de implementaciones dentro de la misma jerarquía.

## Por qué este mecanismo importa en Vertical Slice

El patrón Vertical Slice favorece añadir funcionalidad como **slices nuevos** en lugar de modificar código existente. La inyección de colecciones encaja perfectamente:

- Añades un slice nuevo con su `Handler`.
- El dispatcher central lo recoge automáticamente.
- No tocas el dispatcher, no tocas otros slices.

Open/Closed Principle aplicado al framework: el sistema está **abierto a extensión** (añadir handlers) y **cerrado a modificación** (el dispatcher no cambia).

## Resumen

- `List<T>` inyecta todas las implementaciones de `T`.
- `Map<String, T>` inyecta un map con nombre de bean como clave.
- Para claves de dominio (no nombres Java), construir el map en el constructor a partir de una propiedad del propio bean.
- `@Order` controla el orden de la `List`.
- Sin candidatos: colección vacía, no error.
- Qualifiers permiten filtrar subconjuntos.
- Patrón base de extensibilidad y de Vertical Slice: nuevo slice → nuevo handler → recogido automáticamente.
