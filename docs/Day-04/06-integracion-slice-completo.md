# Integración completa: un slice de principio a fin

Este documento cierra la sesión. Tomamos `PizzaUpdate` como ejemplo y recorremos **todas las capas** que hemos visto, desde el endpoint hasta la base de datos. El objetivo es ver cómo encajan las piezas: ISP, mappers, lookup, dispatcher.

## El slice: `PizzaUpdate`

```java
package com.example.demo.pizza.update;

import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.UUID;

public class PizzaUpdate {

    @RestController
    @RequestMapping("/api/pizzas")
    public static class Endpoint {

        private final CommandDispatcher dispatcher;

        public Endpoint(CommandDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @PutMapping("/{id}")
        public Response update(@PathVariable UUID id, @RequestBody Body body) {
            var command = new Command(id, body.name(), body.description(),
                                       body.url(), body.ingredientIds());
            return dispatcher.dispatch(command);
        }

        public record Body(String name, String description, String url, Set<UUID> ingredientIds) { }
    }

    @Service
    public static class Handler implements CommandHandler<Command, Response> {

        private final IUpdatePizza repository;
        private final LookupResolver lookup;

        public Handler(IUpdatePizza repository, LookupResolver lookup) {
            this.repository = repository;
            this.lookup = lookup;
        }

        @Override
        public Class<Command> commandType() { return Command.class; }

        @Override
        public Response handle(Command command) {
            // 1. IUpdatePizza extiende IGetPizza: get(id) lanza si no existe
            var pizza = repository.get(command.id());

            // 2. Resolver ingredientes por id (lanza si alguno no existe)
            var ingredients = lookup.findAll(Ingredient.class, command.ingredientIds());

            // 3. Mutar el aggregate con la operación de dominio
            pizza.update(command.name(), command.description(), command.url(), ingredients);

            // 4. Persistir
            repository.update(pizza);

            return new Response(pizza.getId());
        }
    }

    public record Command(
        UUID id,
        String name,
        String description,
        String url,
        Set<UUID> ingredientIds
    ) { }

    public record Response(UUID id) { }
}
```

Cuatro pasos en el handler. Cada uno apoyado por una pieza de la arquitectura:

| Paso                          | Pieza                              |
|-------------------------------|------------------------------------|
| `repository.get(id)`          | `IGetPizza` heredado de `IUpdatePizza` (ISP) |
| `lookup.findAll(...)`         | `LookupResolver` + inyección de colección |
| `pizza.update(...)`           | Dominio puro, sin cambios          |
| `repository.update(pizza)`    | `IUpdatePizza` + mapper            |

## Lo que ve cada capa

### El endpoint

Conoce: HTTP, JSON, el `CommandDispatcher`. **No conoce**: dominio, repositorio, JPA.

### El handler

Conoce: comando, dominio, las **interfaces segregadas** que necesita. **No conoce**: JPA, mapper, JPQL.

### El repositorio

Conoce: JPA, mapper, sus interfaces (`IAdd`, `IGet`, `IUpdate`, `IRemove`). **No conoce**: handler, comando, HTTP.

### El mapper

Conoce: dominio, JPA. **No conoce**: repositorio, handler, framework.

### El dominio

Conoce: nada externo. Es la capa más interna. Spring, JPA, HTTP, Jackson... ninguno aparece en el dominio.

## La cadena de dependencias

```
HTTP request
   │
   ▼
PizzaUpdate.Endpoint
   │ inyecta
   ▼
CommandDispatcher
   │ enruta por commandType()
   ▼
PizzaUpdate.Handler
   │ inyecta
   ├──► IUpdatePizza ──► PizzaRepository ──► PizzaJpaRepository (Spring Data)
   │                              │
   │                              └──► PizzaMapper ──► IngredientMapper
   │
   └──► LookupResolver
            │ inyecta List<Lookup<?>>
            └──► IngredientLookup ──► IGetIngredient ──► IngredientRepository
                                                              │
                                                              └──► IngredientJpaRepository
```

Cada flecha es una inyección. Spring resuelve **toda** la cadena por inferencia de tipos: no hay un `@Configuration` que registre nada manualmente.

## El test del slice

Tres tipos de test, cada uno con su rol.

### Test de unidad del handler

Mocks: `IUpdatePizza`, `LookupResolver`. Sin Spring, sin BD.

```java
@ExtendWith(MockitoExtension.class)
class PizzaUpdateHandlerTest {

    @Mock private IUpdatePizza repository;
    @Mock private LookupResolver lookup;
    @InjectMocks private PizzaUpdate.Handler handler;

    @Test
    void actualiza_la_pizza_con_ingredientes_resueltos() {
        var pizzaId = UUID.randomUUID();
        var pizza = Pizza.create(pizzaId, "Vieja", "Vieja", "url", Set.of());
        pizza.clearEvents();

        var ingredientId = UUID.randomUUID();
        var ingredient = Ingredient.create(ingredientId, "Nuevo", new BigDecimal("1"));

        when(repository.get(pizzaId)).thenReturn(pizza);
        when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
            .thenReturn(Set.of(ingredient));

        var command = new PizzaUpdate.Command(
            pizzaId, "Nueva", "Nueva descripción", "nueva-url", Set.of(ingredientId)
        );

        handler.handle(command);

        verify(repository).update(pizza);
        assertEquals("Nueva", pizza.getName());
    }

    @Test
    void propaga_excepcion_si_pizza_no_existe() {
        var pizzaId = UUID.randomUUID();
        when(repository.get(pizzaId))
            .thenThrow(new EntityNotFoundException("Pizza", pizzaId));

        var command = new PizzaUpdate.Command(pizzaId, "x", "y", "z", Set.of());

        assertThrows(EntityNotFoundException.class, () -> handler.handle(command));
    }
}
```

Rápido, no toca BD, no arranca contexto Spring.

### Test del repositorio con `@DataJpaTest`

```java
@DataJpaTest
@Import({ PizzaRepository.class, PizzaMapper.class, IngredientMapper.class })
class PizzaRepositoryTest {

    @Autowired private PizzaRepository repository;
    @Autowired private TestEntityManager em;

    @Test
    void add_y_get_devuelven_la_misma_pizza() {
        var pizza = Pizza.create(UUID.randomUUID(), "Margherita", "Clásica", "url", Set.of());

        repository.add(pizza);
        em.flush();
        em.clear();

        var loaded = repository.get(pizza.getId());

        assertEquals(pizza.getId(), loaded.getId());
        assertEquals("Margherita", loaded.getName());
    }

    @Test
    void get_lanza_si_no_existe() {
        assertThrows(EntityNotFoundException.class, () -> repository.get(UUID.randomUUID()));
    }
}
```

Levanta H2, JPA, los mappers. Confirma que el contrato repositorio + mapper funciona.

### Test end-to-end con `@SpringBootTest`

```java
@SpringBootTest
@AutoConfigureMockMvc
class PizzaUpdateEndToEndTest {
    // PUT /api/pizzas/{id}, comprobar 200, comprobar en BD
}
```

Lento; tener pocos, los suficientes para confirmar el cableado completo.

## La pirámide de tests

```
        E2E (pocos)
      ─────────────────
      Repositorio (algunos)
    ─────────────────────────
    Handler (muchos)
```

La granularidad de las interfaces segregadas hace que **los tests del handler sean ridículamente rápidos**. Esa velocidad es la que permite escribir muchos.

## Lo que hemos conseguido

1. **El dominio sigue intacto.** `Pizza` e `Ingredient` no saben de Spring, JPA, ni nada externo.
2. **Cada handler recibe exactamente lo que necesita.** `PizzaCreate.Handler` no puede borrar pizzas. `PizzaUpdate.Handler` puede leer y actualizar, no añadir ni borrar.
3. **Las FKs se resuelven en un solo punto.** Añadir una nueva entidad referenciada no obliga a tocar ningún resolver existente.
4. **El mapper es código aburrido y explícito.** Cero magia.
5. **La extensibilidad es por composición.** Nuevo slice → nuevo handler → recogido por el dispatcher. Nuevo target de FK → nuevo `Lookup` → recogido por el resolver. Nada más.

## Cierre

El patrón se sostiene sobre tres mecanismos del contenedor de Spring:

- **Inyección por interfaz** para ISP (un bean, varias vistas).
- **Inyección de colecciones** (`List<T>`, `Map<String, T>`) para extensibilidad.
- **`@Component` automático** para descubrimiento.

Nada de esto es exclusivo de Spring. Lo mismo aplicaría en cualquier contenedor IoC serio (Guice, .NET DI, etc.). La filosofía es:

> **El contenedor reúne; nosotros segregamos.**
