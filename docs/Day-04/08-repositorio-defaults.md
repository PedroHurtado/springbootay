# Repositorio con `default methods`: la abstracción que paga

Hemos visto en el documento anterior la versión clásica: una clase concreta con cuatro métodos repetidos en cada repositorio. Funciona, pero escala mal. Aquí proponemos una alternativa que **elimina la repetición sin usar herencia**, apoyándose en una característica de Java que muchos infrautilizan: los `default methods`.

## La idea en una frase

> En lugar de **una clase base** que implementa la lógica, varias **interfaces** que la implementan en sus `default methods`, y una clase concreta que solo cablea las dos piezas variables: el `JpaRepository` interno y el `Mapper`.

## Las piezas

### `Mapper<T, J>`: interfaz común

Para que las interfaces base sepan cómo mapear sin conocer el tipo concreto, todos los mappers implementan la misma interfaz:

```java
public interface Mapper<T, J> {
    J toJpa(T domain);
    T toDomain(J jpa);
}
```

`PizzaMapper` y `IngredientMapper` la implementan:

```java
@Component
public class PizzaMapper implements Mapper<Pizza, PizzaJpa> {
    // toJpa(Pizza), toDomain(PizzaJpa)
}
```

Esto no cambia los mappers del documento anterior; solo les añade un contrato común.

### `RepositoryJpa<T, ID, J>`: la base abstracta sin herencia

```java
public interface RepositoryJpa<T extends EntityBase, ID, J> {
    JpaRepository<J, ID> jpa();
    Mapper<T, J> mapper();
}
```

Tres genéricos:

- `T` — tipo de **dominio** (`Pizza`).
- `ID` — tipo del id (`UUID`).
- `J` — tipo de **persistencia** (`PizzaJpa`).

La restricción `T extends EntityBase` es **importante** y la justificamos en el `IUpdateJpa.update` más abajo.

Dos métodos abstractos: `jpa()` y `mapper()`. Son los **únicos** métodos que la clase concreta tendrá que implementar.

### Interfaces de acción puras

Sin cambios respecto al documento de ISP:

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

Estas interfaces son las que el dominio expone. **Los handlers dependen de estas**, no de las versiones JPA. Eso preserva la separación: el dominio sigue sin saber que existe persistencia.

### Interfaces JPA con `default methods`

Aquí está la magia. Una interfaz por acción, cada una combina su `RepositoryJpa` con su `IXxx` y aporta la implementación por defecto:

```java
public interface IAddJpa<T extends EntityBase, ID, J>
        extends RepositoryJpa<T, ID, J>, IAdd<T> {

    @Override
    default void add(T entity) {
        jpa().save(mapper().toJpa(entity));
    }
}

public interface IGetJpa<T extends EntityBase, ID, J>
        extends RepositoryJpa<T, ID, J>, IGet<T, ID> {

    @Override
    default T get(ID id) {
        return jpa().findById(id)
            .map(mapper()::toDomain)
            .orElseThrow(() -> new EntityNotFoundException(id));
    }
}

public interface IUpdateJpa<T extends EntityBase, ID, J>
        extends IGetJpa<T, ID, J>, IUpdate<T, ID> {

    @Override
    @SuppressWarnings("unchecked")
    default void update(T entity) {
        get((ID) entity.getId());                  // ← lanza si no existe
        jpa().save(mapper().toJpa(entity));
    }
}

public interface IRemoveJpa<T extends EntityBase, ID, J>
        extends IGetJpa<T, ID, J>, IRemove<T, ID> {

    @Override
    default void remove(ID id) {
        get(id);                                    // ← lanza si no existe
        jpa().deleteById(id);
    }
}
```

Tres detalles que vale la pena explicar despacio.

**Detalle 1: `IUpdateJpa extends IGetJpa`, no `IAddJpa`.**

Coherente con el diseño de ISP: para actualizar, primero hay que leer. Y como `IGetJpa` ya implementa `get` como `default`, dentro de `update.default` podemos llamar a `get(id)` directamente — Java resuelve la llamada al `default` heredado. **Composición de defaults**, una característica precisa de Java 8+ que muchos no usan en serio.

**Detalle 2: `(ID) entity.getId()` y el `T extends EntityBase`.**

Esta es la decisión que consensuamos: como toda entidad de dominio extiende `EntityBase`, y `EntityBase.getId()` devuelve `UUID`, podemos extraer el id desde la propia entidad. En la práctica `ID` es siempre `UUID`, así que el cast es seguro. Si el día de mañana algún dominio decide usar un tipo distinto, este es el punto a revisar. Documentado.

**Detalle 3: `IRemoveJpa extends IGetJpa`, pero `remove` recibe `ID`, no `T`.**

Borrar requiere solo el id; no hace falta cargar la entidad completa para luego pasarla. El `get(id)` dentro del `default` está solo para **garantizar que existe** (y lanzar si no) antes del `deleteById`.

## La clase concreta: el momento "ahá"

```java
@Repository
public class PizzaRepository implements
        IAddJpa<Pizza, UUID, PizzaJpa>,
        IUpdateJpa<Pizza, UUID, PizzaJpa>,
        IRemoveJpa<Pizza, UUID, PizzaJpa> {

    private final PizzaJpaRepository jpa;
    private final PizzaMapper mapper;

    public PizzaRepository(PizzaJpaRepository jpa, PizzaMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public JpaRepository<PizzaJpa, UUID> jpa() { return jpa; }

    @Override
    public Mapper<Pizza, PizzaJpa> mapper() { return mapper; }
}
```

**Eso es todo.**

No hay `add`, no hay `get`, no hay `update`, no hay `remove`. **La clase concreta es cableado puro**: declara qué `JpaRepository` y qué `Mapper` usa, y ya. La lógica vive en los `default methods` de las interfaces que implementa.

Si lo comparáis con el documento anterior:

| Aspecto                          | Clásico                     | Con defaults                 |
|----------------------------------|-----------------------------|------------------------------|
| Líneas en `PizzaRepository`      | ~30                         | ~12                          |
| Líneas por nuevo repositorio     | ~30                         | ~12                          |
| Duplicación de lógica add/get/update/remove | sí, por repositorio | no, está en defaults |
| Para cambiar política global     | tocar N clases              | tocar 1 interfaz             |
| Herencia consumida               | ninguna                     | ninguna                      |
| Sobrescribir un comportamiento   | reescribir el método        | sobrescribir el `default`    |

La columna de "herencia consumida" es clave: como hemos usado **interfaces**, la clase concreta sigue **libre** para extender otra clase si algún día lo necesita. La abstracción es por **composición**, no por herencia.

## ¿Y si un repositorio necesita lógica especial?

Imagina que `OrderRepository.add` debe publicar un evento de auditoría además de guardar. Con defaults, simplemente **sobrescribes**:

```java
@Repository
public class OrderRepository implements
        IAddJpa<Order, UUID, OrderJpa>,
        IUpdateJpa<Order, UUID, OrderJpa> {

    private final OrderJpaRepository jpa;
    private final OrderMapper mapper;
    private final AuditPublisher audit;

    // constructor

    @Override public JpaRepository<OrderJpa, UUID> jpa() { return jpa; }
    @Override public Mapper<Order, OrderJpa> mapper() { return mapper; }

    @Override
    public void add(Order order) {
        jpa.save(mapper.toJpa(order));
        audit.publish(new OrderCreated(order.getId()));
    }
}
```

Sobrescribir un `default` en una clase concreta es **idéntico** a sobrescribir un método heredado de una clase base. Java lo resuelve igual. **No pierdes capacidad expresiva**: el día que necesites un caso especial, lo escribes y punto.

## ¿Y si un aggregate es inmutable?

Por ejemplo, `AuditLog` solo se añade y se lee. Nunca se actualiza, nunca se borra.

Con la versión clásica, tenías dos opciones: implementar `update` y `remove` vacíos (feo), o crear una jerarquía base distinta para entidades inmutables (más feo).

Con defaults:

```java
@Repository
public class AuditLogRepository implements
        IAddJpa<AuditLog, UUID, AuditLogJpa>,
        IGetJpa<AuditLog, UUID, AuditLogJpa> {

    // jpa() y mapper(), nada más
}
```

Implementa solo las dos interfaces que necesita. **Cero código muerto**. Esto es algo que la clase base abstracta **no podía hacer**.

## La pregunta inevitable: ¿no es esto magia?

Es una pregunta justa. Repasemos qué hace Java aquí:

1. **`PizzaRepository` implementa varias interfaces con defaults.** Java exige que la clase concreta resuelva cualquier conflicto entre defaults heredados de distintas interfaces. En nuestro caso no hay conflicto: cada `default` está en una jerarquía distinta (`IAddJpa.add`, `IGetJpa.get`, etc.), no se pisan.

2. **Spring inyecta el bean por tipo declarado.** El handler pide `IAddPizza` (que extiende `IAdd<Pizza>`); Spring busca un bean que implemente `IAddPizza` y encuentra `PizzaRepository`. Sin trampa: `PizzaRepository` implementa `IAddJpa<Pizza, ...>`, que extiende `IAdd<Pizza>`, que **es** `IAddPizza` cuando lo declaras así:

```java
public interface IAddPizza extends IAdd<Pizza> { }
```

Spring solo ve tipos e interfaces; los defaults son código Java estándar.

3. **Los defaults se resuelven en runtime.** Cuando llamas a `pizzaRepository.add(pizza)`, la JVM busca el método `add` en la jerarquía y encuentra el `default` de `IAddJpa`. Llama a `this.jpa()` (que devuelve el campo), a `this.mapper()` (idem), y ejecuta. Sin reflexión, sin proxies. Es despacho de método estándar.

No hay magia. Hay **composición de interfaces** y **defaults**. Las dos cosas existen en Java desde 2014.

## Lo que esto enseña

Este es el patrón que quiero que se llevéis. No tanto por el ahorro de líneas (que también), sino por **cuatro lecciones de diseño**:

1. **`default methods` no son una curiosidad.** Son una herramienta de reutilización por composición. Cuando alguien dice "favorece composición sobre herencia", esto es lo que está diciendo.

2. **Las interfaces pueden tener lógica.** Una interfaz no es solo un contrato — desde Java 8 es un contrato **con implementación opcional**. Esto cambia cómo se diseña.

3. **Los genéricos sirven para escribir código una sola vez.** `RepositoryJpa<T, ID, J>` es **una** abstracción que sirve para cualquier aggregate. La clase concreta solo declara los tres tipos.

4. **Una clase puede ser solo cableado.** `PizzaRepository` no tiene lógica. Su trabajo es **decir qué piezas se combinan**. Esa es una forma muy madura de pensar la arquitectura: separar **qué se hace** (defaults en interfaces) de **con qué se hace** (instancias concretas inyectadas).

## Resumen

- `Mapper<T, J>` como interfaz común para que las bases sepan mapear genéricamente.
- `RepositoryJpa<T extends EntityBase, ID, J>` aporta `jpa()` y `mapper()` como métodos abstractos.
- `IAddJpa`, `IGetJpa`, `IUpdateJpa`, `IRemoveJpa` implementan la lógica en `default methods` componiendo `RepositoryJpa` + `IXxx`.
- `IUpdateJpa` y `IRemoveJpa` extienden `IGetJpa` y reusan su `get` default.
- `T extends EntityBase` permite extraer el id genéricamente desde `entity.getId()`.
- La clase concreta es `@Repository`, implementa solo las interfaces JPA que necesita, y aporta exclusivamente `jpa()` y `mapper()`.
- Sobrescribir un `default` cuando haga falta: idéntico a sobrescribir un método heredado.
- Cero herencia consumida; cero código muerto en aggregates con menos operaciones.
- No es magia: es composición de interfaces y `default methods` estándar de Java.
