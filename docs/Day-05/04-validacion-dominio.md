# Validación de dominio (always-valid)

El dominio no se valida con anotaciones. Se valida en código, en el constructor del agregado, dentro de los Value Objects, y en los métodos de comportamiento.

La regla mental es esta:

> Si una instancia de un objeto de dominio existe en memoria, está en estado válido. Sin excepciones.

Esto se llama **always-valid model** y es la línea que separa un dominio decente de un dominio anémico.

## Value Object: el ladrillo más pequeño

Un Value Object encapsula una validación de **tipo primitivo restringido**. `Email`, `Money`, `Quantity`, `Slug`. En vez de pasar `String email` por todo el código (y validarlo en quince sitios), creas un `Email` que **no se puede instanciar mal**.

```java
public record Email(String value) {

    private static final Pattern PATTERN = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    public Email {
        if (value == null || value.isBlank()) {
            throw new InvalidEmailException("Email es obligatorio");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new InvalidEmailException("Formato de email inválido: " + value);
        }
    }
}
```

**Tres cosas que merecen atención:**

1. **Compact constructor del record**. Validas y, si la validación pasa, los campos se asignan automáticamente.
2. **Excepción específica del dominio**. No es `IllegalArgumentException`. Es `InvalidEmailException`. El que captura sabe qué pasó sin parsear mensajes.
3. **No hay setter**. Los records son inmutables. Si quieres "cambiar el email", creas otro Email. Si quieres cambiar el email de un Usuario, el método de comportamiento lo construye y lo reemplaza.

## Money como Value Object con invariante

Money es el ejemplo de manual. Reúne varias invariantes:

```java
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) throw new InvalidMoneyException("Importe obligatorio");
        if (currency == null) throw new InvalidMoneyException("Divisa obligatoria");
        if (amount.scale() > currency.getDefaultFractionDigits()) {
            throw new InvalidMoneyException(
                "Precisión decimal excede la divisa: " + amount.scale()
            );
        }
    }

    public static Money euros(BigDecimal amount) {
        return new Money(amount, Currency.getInstance("EUR"));
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency)) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
        return new Money(amount.add(other.amount), currency);
    }

    public boolean isPositive() {
        return amount.compareTo(BigDecimal.ZERO) > 0;
    }
}
```

Mira lo que pasa con `add`: **no permite sumar euros con dólares**. Si lo intentas, lanza. El compilador no te ayuda aquí (los dos son `Money`), pero el dominio sí.

## Aggregate Root: validación en constructor

El agregado valida sus invariantes en el constructor y en cada método que muta su estado.

```java
public class Pizza {

    private final UUID id;
    private final String name;
    private final Money price;
    private final List<UUID> ingredientIds;

    public Pizza(UUID id, String name, Money price, List<UUID> ingredientIds) {
        // identidad
        if (id == null) {
            throw new InvalidPizzaException("Id obligatorio");
        }
        // nombre
        if (name == null || name.isBlank()) {
            throw new InvalidPizzaException("Nombre obligatorio");
        }
        if (name.length() > 100) {
            throw new InvalidPizzaException("Nombre no puede superar 100 caracteres");
        }
        // precio
        if (price == null) {
            throw new InvalidPizzaException("Precio obligatorio");
        }
        if (!price.isPositive()) {
            throw new InvalidPizzaException("Precio debe ser positivo");
        }
        // ingredientes
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            throw new InvalidPizzaException("Al menos un ingrediente");
        }
        if (new HashSet<>(ingredientIds).size() != ingredientIds.size()) {
            throw new InvalidPizzaException("No se permiten ingredientes duplicados");
        }

        this.id = id;
        this.name = name;
        this.price = price;
        this.ingredientIds = List.copyOf(ingredientIds);  // copia defensiva inmutable
    }

    // métodos de comportamiento que mantienen las invariantes
    public Pizza changePrice(Money newPrice) {
        return new Pizza(id, name, newPrice, ingredientIds);
        // el constructor revalida → si newPrice no es positivo, lanza
    }
}
```

**Patrones que se repiten:**

- Validar **todo lo que el negocio necesita**, en orden lógico (identidad → datos obligatorios → reglas de negocio).
- **Copia defensiva** de colecciones (`List.copyOf`). Si guardas la lista que te pasaron, alguien puede mutarla desde fuera y romper la invariante.
- Métodos de mutación **devuelven nueva instancia** (si el agregado es inmutable) o validan antes de mutar (si es mutable).

## La excepción de dominio

`InvalidPizzaException`, `InvalidEmailException`, `CurrencyMismatchException`... no son `RuntimeException` cualesquiera. Son **eventos del dominio negativos** que el resto del sistema sabe traducir.

Una jerarquía mínima:

```java
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) {
        super(message);
    }
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}

public class InvalidPizzaException extends DomainException {
    public InvalidPizzaException(String message) {
        super(message);
    }
}

public class PizzaNotFoundException extends DomainException {
    public PizzaNotFoundException(UUID id) {
        super("Pizza no encontrada: " + id);
    }
}
```

Y el ControllerAdvice las traduce:

```java
@RestControllerAdvice
public class DomainExceptionAdvice {

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handle(DomainException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Domain rule violation");
        problem.setDetail(ex.getMessage());
        problem.setType(URI.create("https://fudie.eu/problems/domain"));
        return problem;
    }
}
```

Fijaos: el código es **422 Unprocessable Entity**, no 400. La diferencia:

- **400 Bad Request** = el input no es procesable (sintaxis, formato). Aquí es donde caen las violaciones de Bean Validation.
- **422 Unprocessable Entity** = el input es procesable, pero **viola reglas de negocio**. Aquí caen las violaciones de dominio.

Es una distinción semántica que ayuda a los clientes a saber si pueden reintentar tras corregir formato o si la regla violada es de negocio.

## Validación con dependencias externas

Algunas validaciones necesitan consultar otra cosa: "este email no puede existir ya", "el ingrediente debe existir en el catálogo".

**Eso no va en el constructor del agregado.** El constructor solo conoce los datos que le pasas. No tiene repositorios inyectados. No puede hacer queries.

Esas validaciones viven en el **Handler** (caso de uso), porque necesitan orquestar:

```java
public class PizzaCreate {

    public record Command(/* ... */) {}
    public record Response(UUID id) {}

    @Service
    public static class Handler {

        private final IAdd<Pizza> repository;
        private final IGet<Ingredient, UUID> ingredients;

        public Handler(IAdd<Pizza> repository, IGet<Ingredient, UUID> ingredients) {
            this.repository = repository;
            this.ingredients = ingredients;
        }

        public Response handle(Command command) {
            // 1. resolver FKs → lanza IngredientNotFoundException si falta alguno
            List<UUID> resolved = command.ingredientIds().stream()
                .map(ingredients::get)   // get lanza si no existe
                .map(Ingredient::id)
                .toList();

            // 2. construir el agregado → lanza InvalidPizzaException si los datos no cumplen
            Pizza pizza = new Pizza(
                UUID.randomUUID(),
                command.name(),
                Money.euros(command.price()),
                resolved
            );

            // 3. persistir
            repository.add(pizza);
            return new Response(pizza.id());
        }
    }
}
```

**Tres capas de validación** en este flujo:

1. **Command** (`@Valid`): forma del input. Si falla → 400.
2. **Handler**: existencia de referencias externas. Si falla → 404 o 422.
3. **Dominio** (constructor): invariantes del modelo. Si falla → 422.

Cada una en su sitio. Ninguna duplica la otra **porque no validan lo mismo**.

## Self-encapsulation: ya nadie mira los anotaciones del Command

Una vez el dominio valida bien:

- Los handlers no validan a mano "if name is null".
- Los services no comprueban "if price <= 0".
- Los tests del dominio no pasan por Spring ni por Controllers.

Si alguna vez el dominio se invoca desde un job programado, desde un consumer de Pub/Sub, desde un script de migración... **sigue siendo seguro**. No hay un `@Valid` mágico que se haya saltado. Las invariantes son del propio modelo.

Esa es la diferencia entre dominio robusto y dominio decorativo.
