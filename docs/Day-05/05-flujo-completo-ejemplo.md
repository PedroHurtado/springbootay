# Flujo completo: Command + Handler + Dominio validando juntos

Este documento junta todo lo visto en un slice real. PizzaCreate de principio a fin, con las tres capas de validación coexistiendo sin pisarse.

## El slice completo

```java
@RestController
@RequestMapping("/pizzas")
public class PizzaCreate {

    private final Handler handler;

    public PizzaCreate(Handler handler) {
        this.handler = handler;
    }

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody Command command) {
        Response response = handler.handle(command);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    // ---------- Command: validación de FORMA ----------
    public record Command(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "Máximo 100 caracteres")
        String name,

        @NotNull(message = "El precio es obligatorio")
        @Positive(message = "El precio debe ser positivo")
        BigDecimal price,

        @NotEmpty(message = "Al menos un ingrediente")
        List<@NotNull UUID> ingredientIds
    ) {}

    // ---------- Response ----------
    public record Response(UUID id) {}

    // ---------- Handler: validación de REFERENCIAS EXTERNAS ----------
    @Service
    public static class Handler {

        private final IAdd<Pizza> repository;
        private final IGet<Ingredient, UUID> ingredients;

        public Handler(IAdd<Pizza> repository, IGet<Ingredient, UUID> ingredients) {
            this.repository = repository;
            this.ingredients = ingredients;
        }

        public Response handle(Command command) {
            // Resuelve todos los ingredientes. Si alguno no existe,
            // el get del IGet lanza IngredientNotFoundException.
            List<UUID> resolvedIngredients = command.ingredientIds().stream()
                .map(ingredients::get)
                .map(Ingredient::id)
                .toList();

            // El constructor de Pizza valida invariantes del dominio.
            // Si alguna falla, lanza InvalidPizzaException.
            Pizza pizza = new Pizza(
                UUID.randomUUID(),
                command.name(),
                Money.euros(command.price()),
                resolvedIngredients
            );

            repository.add(pizza);
            return new Response(pizza.id());
        }
    }
}
```

## Las tres capas, otra vez, con el flujo de un request

### Request 1: JSON con campo `name` vacío

```json
POST /pizzas
{
    "name": "",
    "price": 12.50,
    "ingredientIds": ["uuid-1", "uuid-2"]
}
```

Recorrido:

1. Spring deserializa el JSON al Command.
2. `@Valid` dispara Bean Validation.
3. `@NotBlank` sobre `name` falla.
4. Spring lanza `MethodArgumentNotValidException` **antes de invocar el método** del Controller.
5. El `@RestControllerAdvice` la captura → **400 Bad Request**.

El Handler nunca se ejecuta. El dominio nunca se construye. **Fail fast en el borde**.

### Request 2: JSON correcto pero con un `ingredientId` que no existe

```json
POST /pizzas
{
    "name": "Margherita",
    "price": 12.50,
    "ingredientIds": ["uuid-no-existe"]
}
```

Recorrido:

1. Spring deserializa, `@Valid` pasa (todos los campos cumplen forma).
2. El Controller llama a `handler.handle(command)`.
3. El Handler intenta resolver el primer ingrediente: `ingredients.get(uuid-no-existe)`.
4. `IGet.get` lanza `IngredientNotFoundException`.
5. El advice la captura → **404 Not Found** o **422 Unprocessable Entity** (según cómo lo modelemos).

El constructor de Pizza nunca se invoca.

### Request 3: JSON correcto, ingredientes existen, pero hay duplicados

```json
POST /pizzas
{
    "name": "Margherita",
    "price": 12.50,
    "ingredientIds": ["uuid-1", "uuid-1"]
}
```

Recorrido:

1. Spring deserializa, `@Valid` pasa.
2. El Handler resuelve los dos ingredientes (los dos existen, son el mismo).
3. Llama a `new Pizza(...)` pasando la lista con duplicados.
4. El constructor de Pizza detecta el duplicado y lanza `InvalidPizzaException`.
5. El advice la captura → **422 Unprocessable Entity**.

`@NotEmpty` no detecta duplicados. Bean Validation no detecta duplicados sin una constraint custom. Pero el **dominio sí** los detecta porque para él es una invariante del modelo.

### Request 4: todo correcto

1. Spring deserializa, `@Valid` pasa.
2. El Handler resuelve los ingredientes.
3. El constructor de Pizza pasa todas las invariantes.
4. `repository.add(pizza)` persiste.
5. **201 Created** con el body de Response.

## Por qué hay aparente "duplicación" y por qué no es un problema

Si miras `@Positive` en `Command.price` y `if (!price.isPositive())` en el constructor de `Pizza`, parece que validas dos veces lo mismo.

No:

- La del Command **rechaza el request HTTP** con un 400 amable. Sin esa, el cliente recibiría un 422 desde el dominio, lo cual es funcionalmente correcto pero **menos preciso semánticamente**.
- La del dominio **garantiza la invariante del modelo**. Sin esa, alguien podría construir una `Pizza` con precio cero desde un test, desde un seeder, desde un consumer.

Quitar cualquiera de las dos rompe algo:

| Si quitamos... | Lo que perdemos |
|----------------|------------------|
| `@Positive` del Command | Errores de input devuelven 422 en vez de 400. Cliente confuso. |
| `if (!price.isPositive())` del dominio | El dominio depende de que alguien valide antes. Frágil. |

El coste de mantener las dos es minúsculo. El beneficio (fail-fast preciso + dominio always-valid) compensa.

## Cuándo una capa es suficiente

No todo necesita las tres capas.

**Solo Command**: validaciones que son **puramente HTTP** y no tienen reflejo en el modelo.

```java
public record CreateSessionCommand(
    @NotBlank String username,
    @NotBlank String password,
    @AssertTrue(message = "Debes aceptar los términos") boolean acceptedTerms
) {}
```

`acceptedTerms` no va al dominio. Es un check de UI. Solo Command.

**Solo Dominio**: reglas que dependen de **estado interno** del agregado.

```java
public class Reservation {

    private ReservationStatus status;

    public void cancel() {
        if (status == ReservationStatus.PAID) {
            throw new CannotCancelPaidReservationException(id);
        }
        if (startsIn().toHours() < 24) {
            throw new CancellationWindowExpiredException(id);
        }
        this.status = ReservationStatus.CANCELLED;
    }
}
```

Esto **no se puede expresar** con Bean Validation. Va en el dominio. El Command de cancelación quizá solo lleva el ID y ningún `@NotNull` más.

## Cómo se ve el Handler probado sin Spring

Una ventaja de tener las invariantes en el dominio es que puedes testear el dominio puro:

```java
@Test
void pizza_sin_ingredientes_lanza_excepcion() {
    assertThrows(InvalidPizzaException.class, () ->
        new Pizza(UUID.randomUUID(), "Test", Money.euros(BigDecimal.TEN), List.of())
    );
}

@Test
void pizza_con_precio_cero_lanza_excepcion() {
    assertThrows(InvalidPizzaException.class, () ->
        new Pizza(UUID.randomUUID(), "Test", Money.euros(BigDecimal.ZERO),
                  List.of(UUID.randomUUID()))
    );
}
```

Ni Spring, ni `@SpringBootTest`, ni `@MockBean`, ni base de datos. Tests de microsegundos. Esa es la diferencia entre validar en el sitio adecuado y no hacerlo.
