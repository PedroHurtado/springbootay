# Lookup de FK con inyección de colecciones

## El problema

Cuando un handler crea o actualiza una `Pizza`, el comando trae **ids** de ingredientes, no objetos `Ingredient`. Hay que resolver esos ids a entidades de dominio antes de construir la Pizza:

```java
public Response handle(Command command) {
    var ingredients = command.ingredientIds().stream()
        .map(/* ??? */)
        .collect(toSet());

    var pizza = Pizza.create(UUID.randomUUID(), command.name(), /* ... */, ingredients);
    repository.add(pizza);
    return new Response(pizza.getId());
}
```

Lo natural sería inyectar `IGetIngredient` y llamar a `get(id)` por cada id. Funciona, pero hay un problema más amplio: en un sistema con muchos aggregates, **cualquier handler que necesite lookups acaba inyectando cinco o seis repositorios distintos** para resolver FKs.

Y el problema se agrava: cada `IGet<T, ID>.get(id)` que diseñemos puede devolver `T` o `Optional<T>`. Si devolvemos `Optional`, el caller acaba escribiendo `orElseThrow(...)` **siempre**. Ese ceremonial es ruido.

## La decisión: `get` lanza, no retorna `Optional`

Dos posturas existen en la comunidad Java:

**Postura A**: `findById` devuelve `Optional<T>` porque "el caller decide qué hacer si no existe". Es la línea oficial de Spring Data y de muchos defensores del estilo funcional.

**Postura B**: si el caller **siempre** convierte el `Optional` vacío en una excepción (porque la FK que está resolviendo *debería* existir), mover el throw a la operación de lookup elimina el ceremonial y refleja la realidad del dominio.

Adoptamos **B** para FK lookup. La justificación es operativa: cuando estás resolviendo una FK que llega en un comando, **no existe** un caso legítimo en el que "no encontrar" sea un resultado válido. Si llega `ingredientId = X` y no hay tal `Ingredient`, eso es un error del cliente: id inválido, ingrediente borrado, race condition. El resultado siempre es un 404 (o 400, dependiendo del caso). Empaquetarlo en `Optional` solo para hacer `orElseThrow` en cada caller es duplicación.

```java
public interface IGet<T, ID> {
    T get(ID id);  // lanza EntityNotFoundException si no existe
}
```

## La pieza nueva: `ExistenceChecker` / `Lookup` por tipo

Vamos a usar la **inyección de colecciones** (la del documento que has compartido) para tener **un único punto** que resuelve FKs por **tipo de entidad**. El handler no inyecta repositorios; inyecta un resolver.

Diseño:

```java
public interface Lookup<T> {
    Class<T> type();
    T find(UUID id); // lanza si no existe
}
```

Cada repositorio que sea **target de FK** (es decir, cada aggregate cuyas instancias se referencian desde otros aggregates) registra un `Lookup<T>`.

```java
@Component
public class IngredientLookup implements Lookup<Ingredient> {

    private final IGetIngredient repository;

    public IngredientLookup(IGetIngredient repository) {
        this.repository = repository;
    }

    @Override
    public Class<Ingredient> type() {
        return Ingredient.class;
    }

    @Override
    public Ingredient find(UUID id) {
        return repository.get(id); // ya lanza EntityNotFoundException
    }
}
```

## El resolver central

Aplicamos exactamente el patrón del documento de inyección de colecciones: el resolver inyecta `List<Lookup<?>>` y construye un map por `type()`.

```java
@Service
public class LookupResolver {

    private final Map<Class<?>, Lookup<?>> lookups;

    public LookupResolver(List<Lookup<?>> lookups) {
        this.lookups = lookups.stream()
            .collect(toMap(Lookup::type, identity()));
    }

    @SuppressWarnings("unchecked")
    public <T> T find(Class<T> type, UUID id) {
        Lookup<T> lookup = (Lookup<T>) lookups.get(type);
        if (lookup == null) {
            throw new NoLookupRegisteredException(type);
        }
        return lookup.find(id);
    }

    public <T> Set<T> findAll(Class<T> type, Collection<UUID> ids) {
        return ids.stream()
            .map(id -> find(type, id))
            .collect(toSet());
    }
}
```

Lo importante: **el handler ya no inyecta `IGetIngredient`**. Inyecta `LookupResolver` y resuelve cualquier FK que necesite.

## El handler limpio

```java
public class PizzaCreate {

    @RestController
    @RequestMapping("/api/pizzas")
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

        private final IAddPizza repository;
        private final LookupResolver lookup;

        public Handler(IAddPizza repository, LookupResolver lookup) {
            this.repository = repository;
            this.lookup = lookup;
        }

        @Override
        public Class<Command> commandType() { return Command.class; }

        @Override
        public Response handle(Command command) {
            var ingredients = lookup.findAll(Ingredient.class, command.ingredientIds());

            var pizza = Pizza.create(
                UUID.randomUUID(),
                command.name(),
                command.description(),
                command.url(),
                ingredients
            );

            repository.add(pizza);
            return new Response(pizza.getId());
        }
    }

    public record Command(
        String name,
        String description,
        String url,
        Set<UUID> ingredientIds
    ) { }

    public record Response(UUID id) { }
}
```

El handler **dice lo que hace**: resuelve los ingredientes por id, construye la pizza, la añade. No conoce qué repositorio resuelve `Ingredient` ni cómo se busca. La pieza de plomería está confinada al `LookupResolver`.

## Por qué no `Optional`

Trazamos el flujo:

1. El comando entra con `ingredientIds = {a, b, c}`.
2. Resolver intenta `find(Ingredient.class, a)`.
3. Si `a` no existe: **el cliente nos pasó un id inválido**. La respuesta correcta es **error**, no "Pizza sin ingrediente a".

Construir la `Pizza` con un ingrediente menos del que el usuario pidió es **silencioso y peligroso**. Lanzar excepción es **ruidoso, explícito y correcto**. El handler de error global la traducirá a 404 o 400 según convenga.

Comparemos los dos estilos:

**Con `Optional`:**

```java
public Response handle(Command command) {
    var ingredients = command.ingredientIds().stream()
        .map(id -> ingredientRepo.get(id)
            .orElseThrow(() -> new EntityNotFoundException("Ingredient", id)))
        .collect(toSet());
    // ...
}
```

**Sin `Optional`:**

```java
public Response handle(Command command) {
    var ingredients = lookup.findAll(Ingredient.class, command.ingredientIds());
    // ...
}
```

La segunda forma se lee como **pseudocódigo del negocio**. La primera tiene ceremonial.

## El test del handler

El test no necesita mockear repositorios; mockea el `LookupResolver`:

```java
@ExtendWith(MockitoExtension.class)
class PizzaCreateHandlerTest {

    @Mock private IAddPizza repository;
    @Mock private LookupResolver lookup;

    @InjectMocks private PizzaCreate.Handler handler;

    @Test
    void crea_pizza_con_ingredientes_resueltos() {
        var ingredientId = UUID.randomUUID();
        var ingredient = Ingredient.create(ingredientId, "Tomate", new BigDecimal("0.50"));

        when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
            .thenReturn(Set.of(ingredient));

        var command = new PizzaCreate.Command(
            "Margherita", "Pizza clásica", "https://...", Set.of(ingredientId)
        );

        var response = handler.handle(command);

        assertNotNull(response.id());
        verify(repository).add(any(Pizza.class));
    }

    @Test
    void lanza_si_ingrediente_no_existe() {
        var ingredientId = UUID.randomUUID();
        when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
            .thenThrow(new EntityNotFoundException("Ingredient", ingredientId));

        var command = new PizzaCreate.Command(
            "X", "Y", "Z", Set.of(ingredientId)
        );

        assertThrows(EntityNotFoundException.class, () -> handler.handle(command));
    }
}
```

Mock superficial, intención clara.

## Extensibilidad: añadir un nuevo aggregate target

Mañana añadimos `Category` y `Pizza` referencia a una `Category`. ¿Qué hay que hacer?

1. Crear `Category` en dominio, `CategoryJpa`, `CategoryMapper`, `CategoryRepository` (con sus interfaces `IAddCategory`, `IGetCategory`, etc.).
2. Crear `CategoryLookup`:

```java
@Component
public class CategoryLookup implements Lookup<Category> {

    private final IGetCategory repository;

    public CategoryLookup(IGetCategory repository) {
        this.repository = repository;
    }

    @Override public Class<Category> type() { return Category.class; }
    @Override public Category find(UUID id) { return repository.get(id); }
}
```

3. **No tocamos `LookupResolver`.** Spring lo recoge automáticamente vía inyección de colección.

4. En el handler:

```java
var category = lookup.find(Category.class, command.categoryId());
```

Cero modificaciones en código existente. **Open/Closed aplicado al framework de FK lookup**, exactamente como en el documento que has compartido.

## ¿Cuándo no usar este patrón?

El patrón está pensado para **resolver FKs** durante la ejecución de un comando. No es:

- **Consultas de listado paginado**: usa Spring Data directamente.
- **Joins multinivel para reporting**: usa `@Query` con JPQL.
- **Acceso al aggregate principal del handler**: usa la interfaz segregada (`IAddPizza`, `IGetPizza`, etc.).

`LookupResolver` resuelve **referencias** desde otros aggregates, no es un repositorio universal.

## Resumen

- `get(id)` lanza `EntityNotFoundException`. No devolvemos `Optional` en lookup de FK.
- Cada aggregate target define un `Lookup<T>` (`@Component`).
- `LookupResolver` inyecta `List<Lookup<?>>` y construye un map por `type()`.
- Los handlers inyectan `LookupResolver`, no repositorios sueltos por FK.
- Resolver una FK: `lookup.find(Category.class, id)`.
- Resolver una colección: `lookup.findAll(Ingredient.class, ids)`.
- Añadir un nuevo target: crear `Lookup<NewType>` y nada más. El resolver lo recoge automáticamente.
- ISP intacto: el handler de `PizzaCreate` solo ve `IAddPizza` + `LookupResolver`, ninguno con métodos que no necesite.
