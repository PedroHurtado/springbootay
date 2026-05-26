# Principio de Segregación de Interfaz aplicado a repositorios

## El problema con `JpaRepository`

`JpaRepository<T, ID>` ofrece de un plumazo: `findAll`, `findById`, `save`, `saveAll`, `delete`, `deleteById`, `count`, `existsById`, `flush`, `getReferenceById`, paginación, sorting...

```java
public interface JpaRepository<T, ID> extends ListCrudRepository<T, ID>,
    ListPagingAndSortingRepository<T, ID>, QueryByExampleExecutor<T> {
    // ~20 métodos heredados
}
```

Inyectar `JpaRepository<PizzaJpa, UUID>` en un handler es violar el **Principio de Segregación de Interfaz** (ISP): el handler recibe muchísimos métodos que **no necesita**, no debería conocer y **no debería poder llamar**.

> "No client should be forced to depend on methods it does not use." — Robert C. Martin

Si un handler solo añade pizzas, ¿por qué recibe acceso a `deleteAll`, `flush` o `saveAll`?

## La consecuencia práctica

Mezclar acceso a `findAll` y `deleteAll` en el mismo punto crea handlers que **pueden hacer cualquier cosa**. La superficie de la dependencia explota. El test del handler tiene que mockear toda la interfaz aunque solo use un método. Y peor: si alguien en el futuro mete `repository.deleteAll()` dentro de un handler de creación, **el código compila** y nadie lo nota hasta producción.

ISP no es purismo académico. Es una herramienta para que **el código solo pueda hacer aquello para lo que existe**.

## La propuesta: interfaces pequeñas y orientadas a la acción

Una interfaz por **acción**, no por entidad. Y en lo posible, parametrizadas por el tipo de dominio (no por la entidad JPA).

```java
public interface IAdd<T> {
    void add(T entity);
}

public interface IGet<T, ID> {
    T get(ID id);
}

public interface IUpdate<T, ID> extends IGet<T, ID> {
    void update(T entity);
}

public interface IRemove<T, ID> extends IGet<T, ID> {
    void remove(ID id);
}
```

Notas de diseño:

- `IGet` es la base de `IUpdate` y `IRemove`. La justificación: para actualizar o borrar, primero hay que **leer**. La interfaz lo refleja: si tienes `IUpdate`, tienes `get` gratis.
- `IAdd` no extiende `IGet`. Añadir no requiere leer.
- `get(ID id)` devuelve `T`, **no `Optional<T>`**. Si no existe, lanza `EntityNotFoundException`. Esta decisión la justificamos en el documento de lookup de FK; resumen: el caller habitual es "carga el aggregate para operar sobre él" y el `Optional` se acaba colapsando siempre en `orElseThrow(...)`. Mover el throw al repositorio elimina el ceremonial.

## Tipos en T: dominio, no JPA

Cuando declaramos `IAdd<Pizza>`, el `Pizza` es el **del dominio**, no la entidad JPA. El handler trabaja con dominio:

```java
public interface IAddPizza extends IAdd<Pizza> { }
public interface IGetPizza extends IGet<Pizza, UUID> { }
public interface IUpdatePizza extends IUpdate<Pizza, UUID> { }
public interface IRemovePizza extends IRemove<Pizza, UUID> { }
```

La implementación se encarga de mapear `Pizza ↔ PizzaJpa`. Ese es el punto del documento de mappers manuales: la frontera entre dominio y persistencia.

## La implementación

Una sola clase implementa **todas** las interfaces de Pizza. No multiplicamos clases artificialmente: el aggregate vive entero en un repositorio.

```java
@Service
public class PizzaRepository implements IAddPizza, IUpdatePizza, IRemovePizza {

    private final PizzaJpaRepository jpa;
    private final PizzaMapper mapper;

    public PizzaRepository(PizzaJpaRepository jpa, PizzaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public void add(Pizza pizza) {
        jpa.save(mapper.toJpa(pizza));
    }

    @Override
    public Pizza get(UUID id) {
        return jpa.findById(id)
            .map(mapper::toDomain)
            .orElseThrow(() -> new EntityNotFoundException("Pizza", id));
    }

    @Override
    public void update(Pizza pizza) {
        // get(...) garantiza que existe, lanza si no
        get(pizza.getId());
        jpa.save(mapper.toJpa(pizza));
    }

    @Override
    public void remove(UUID id) {
        get(id); // garantiza existencia
        jpa.deleteById(id);
    }
}
```

`PizzaJpaRepository` sigue siendo `extends JpaRepository<PizzaJpa, UUID>`. La diferencia es que **nadie lo inyecta excepto este `PizzaRepository`**. El acceso "pleno" a JPA está confinado a la clase que sabe usarlo.

## Inyección en handlers: solo lo que necesitan

Aquí es donde ISP paga sus dividendos:

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

        public Handler(IAddPizza repository) {
            this.repository = repository;
        }

        @Override
        public Class<Command> commandType() { return Command.class; }

        @Override
        public Response handle(Command command) {
            var pizza = Pizza.create(
                UUID.randomUUID(),
                command.name(),
                command.description(),
                command.url(),
                Set.of() // veremos esto en el documento de FK lookup
            );
            repository.add(pizza);
            return new Response(pizza.getId());
        }
    }

    public record Command(String name, String description, String url) { }
    public record Response(UUID id) { }
}
```

El handler de **creación** depende solo de `IAddPizza`. No tiene `get`. No tiene `update`. No tiene `remove`. **No puede** llamarlos aunque quisiera.

Compara con el handler de **borrado**:

```java
@Service
public static class Handler implements CommandHandler<Command, Response> {

    private final IRemovePizza repository;

    public Handler(IRemovePizza repository) {
        this.repository = repository;
    }

    public Response handle(Command command) {
        repository.remove(command.id()); // get() interno garantiza existencia
        return new Response();
    }
}
```

`IRemovePizza` da acceso a `remove` y a `get` (heredado). Nada más.

## Spring resuelve la inyección sin trampa

Aunque `PizzaRepository` implementa varias interfaces, Spring lo trata como **un único bean** que cumple varios tipos. Al inyectar `IAddPizza`, Spring busca beans que implementen esa interfaz y encuentra `PizzaRepository`. Lo mismo con `IUpdatePizza`. Misma instancia, distintas vistas.

```
PizzaRepository (singleton)
  ├── visto como IAddPizza por PizzaCreate.Handler
  ├── visto como IUpdatePizza por PizzaUpdate.Handler
  └── visto como IRemovePizza por PizzaRemove.Handler
```

No hay duplicación de beans, no hay configuración extra. Spring lo hace por inferencia de tipos.

## Beneficios concretos

1. **Tests más limpios.** El test del handler de creación mockea `IAddPizza` (un método). No hay `Mockito.when(repository.findById(any())).thenReturn(...)` irrelevante.

2. **Refactor seguro.** Si añadimos un método a `IGetPizza`, solo afecta a quien necesite `get`. Los handlers de creación no se enteran.

3. **Intención visible.** El tipo del parámetro del constructor **declara** lo que el handler hace. Leer la firma del handler te dice si crea, lee o borra. No necesitas mirar el body.

4. **Imposibilidad de errores.** El handler de creación literalmente **no puede** borrar. La interfaz no expone el método. Es un error eliminado en tiempo de compilación.

## Comparación con Fudie-Framework

En .NET tenemos `IAdd<T>`, `IGet<T, TKey>`, `IUpdate<T, TKey>`, `IRemove<T, TKey>` con la misma semántica. La traducción a Java es directa. Lo único que cambia:

- Java no tiene `default interface members` con la misma ergonomía que C# 8+, pero para este patrón no nos hace falta.
- `IGet` lanza excepción en ambos. En .NET es `EntityNotFoundException`, en Java haremos la nuestra.
- En .NET las implementaciones se registran como `Scoped`. En Spring, por defecto son singleton; el ciclo de vida lo maneja el `EntityManager` (que sí es scoped por request).

El patrón es el mismo. La filosofía es la misma: **dar a cada cliente exactamente lo que necesita, ni más ni menos**.

## Resumen

- `JpaRepository<T, ID>` da demasiado: viola ISP.
- Definimos `IAdd<T>`, `IGet<T, ID>`, `IUpdate<T, ID> extends IGet`, `IRemove<T, ID> extends IGet`.
- Por entidad de dominio: `IAddPizza`, `IGetPizza`, `IUpdatePizza`, `IRemovePizza`.
- Una sola implementación (`PizzaRepository`) que implementa todas las interfaces y delega en el JPA interno + mapper.
- Los handlers inyectan **solo** la interfaz que necesitan.
- `get(id)` devuelve `T` o lanza; no `Optional`.
- Spring inyecta la misma instancia bajo distintos tipos sin configuración extra.
