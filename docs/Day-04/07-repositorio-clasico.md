# Implementación clásica del repositorio

Antes de proponer nuestra versión, veamos **lo que se encuentran ahí fuera**. En cualquier proyecto Spring serio, un repositorio que cumple con las interfaces segregadas que hemos definido (`IAdd`, `IGet`, `IUpdate`, `IRemove`) se implementa así:

```java
@Repository
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
            .orElseThrow(() -> new EntityNotFoundException(Pizza.class, id));
    }

    @Override
    public void update(Pizza pizza) {
        get(pizza.getId()); // verifica existencia
        jpa.save(mapper.toJpa(pizza));
    }

    @Override
    public void remove(UUID id) {
        get(id);
        jpa.deleteById(id);
    }
}
```

Esto **es lo que vais a ver** en el 95% de los proyectos. Es lo que sale en los libros, en los tutoriales, en los repos de ejemplo. Y antes de criticarlo, hay que entender lo que tiene **a favor**.

## Lo que está bien

**1. Es trivial de leer.** No hay magia. Abres la clase, ves cuatro métodos, sabes exactamente qué hace cada uno. Un programador que llega nuevo al equipo lo entiende en treinta segundos.

**2. Es trivial de debuggear.** Pones un breakpoint en `add`, paras ahí. Sin saltos por jerarquías de interfaces, sin defaults heredados, sin proxies.

**3. Es trivial de modificar.** Si mañana `add` necesita publicar un evento, lanzar un dominio enriquecido o validar algo, abres la clase y lo añades en el método. No tienes que pensar en si rompe una abstracción base.

**4. Cumple ISP perfectamente.** Implementa las tres interfaces que necesita el dominio y nada más. Los handlers reciben solo lo que necesitan. El objetivo del documento de ISP **ya está conseguido**.

Esto no es un strawman. Es código profesional, limpio, defendible.

## Lo que está mal

Ahora imaginad que en lugar de `Pizza` tenemos diez aggregates: `Pizza`, `Ingredient`, `Category`, `Order`, `Customer`, `Restaurant`, `Table`, `Reservation`, `Payment`, `Invoice`.

Diez clases `XxxRepository`. Cuarenta métodos `add`/`get`/`update`/`remove`. **Cuatrocientas líneas** de:

```java
jpa.save(mapper.toJpa(entity));
```

```java
jpa.findById(id)
    .map(mapper::toDomain)
    .orElseThrow(() -> new EntityNotFoundException(Xxx.class, id));
```

```java
get(entity.getId());
jpa.save(mapper.toJpa(entity));
```

```java
get(id);
jpa.deleteById(id);
```

**Las mismas cuatro líneas, replicadas diez veces, cambiando solo `Xxx` por el nombre del aggregate**.

Y peor: si un día decidimos que `update` debe disparar también un `flush` explícito, hay que ir a las **diez** clases y modificarlas. Si descubrimos un bug en cómo construimos la excepción, lo mismo. Si añadimos logging, lo mismo.

Es código que **no tiene decisiones**. Solo tiene mecánica.

## El olor de la repetición

Cuando ves cuatro líneas de código repetidas en diez sitios, hay tres preguntas que hacer:

1. ¿Es realmente la **misma** lógica o solo se parece?
2. Si extraigo una abstracción, ¿me obliga a casos especiales feos para los outliers?
3. ¿Vale más la cercanía al sitio donde se usa, o vale más la unicidad?

Para nuestro caso, las respuestas son: **sí, no, unicidad**.

- **Sí** es la misma lógica: `add`, `get`, `update`, `remove` hacen exactamente lo mismo para cualquier aggregate.
- **No** hay outliers preocupantes: si un repositorio necesita un `add` distinto (por ejemplo, validar unicidad antes), puede sobrescribir el comportamiento por defecto sin perder el resto.
- **La unicidad gana**: queremos que si mañana cambia la política de "qué hacer al borrar", se cambie **en un sitio**, no en diez.

Cuando las tres respuestas alinean, la abstracción **paga**.

## El paso intermedio: clase base abstracta

La forma "ortodoxa" de evitar la repetición sería una clase base abstracta:

```java
public abstract class BaseRepository<T extends EntityBase, ID, J> {

    protected abstract JpaRepository<J, ID> jpa();
    protected abstract Mapper<T, J> mapper();

    public void add(T entity) {
        jpa().save(mapper().toJpa(entity));
    }

    public T get(ID id) {
        return jpa().findById(id)
            .map(mapper()::toDomain)
            .orElseThrow(() -> new EntityNotFoundException(/* ... */));
    }

    // ...
}

@Repository
public class PizzaRepository extends BaseRepository<Pizza, UUID, PizzaJpa>
    implements IAddPizza, IUpdatePizza, IRemovePizza {

    private final PizzaJpaRepository jpa;
    private final PizzaMapper mapper;

    // constructor

    @Override protected JpaRepository<PizzaJpa, UUID> jpa() { return jpa; }
    @Override protected Mapper<Pizza, PizzaJpa> mapper() { return mapper; }
}
```

Funciona. Elimina la duplicación. Pero introduce un problema nuevo: **herencia para reutilizar código**.

Cualquiera que haya leído *Effective Java* (Item 18: "Favor composition over inheritance") sabe que esto es un olor. La clase base **secuestra** el slot de herencia única de Java. Si mañana `PizzaRepository` necesitase extender otra cosa, no puede.

Además, la clase base obliga a **implementar todos** los métodos. Si un repositorio solo necesita `add` y `get` (porque sus aggregates son inmutables), tiene que vivir con `update` y `remove` colgando.

La clase base abstracta es **la solución de los 90**. Java 8 nos dio una herramienta mejor.

## Lo que viene

En el documento siguiente vamos a hacer lo mismo, pero **componiendo interfaces con `default methods`**. El resultado:

- La clase concreta **no tiene métodos**. Solo cablea `jpa()` y `mapper()`.
- Cada interfaz aporta su propio `default`, y se combinan por **composición**, no por herencia.
- Un repositorio que solo necesite `add` y `get` implementa solo esas dos interfaces. Cero código muerto.
- Si necesita lógica especial en algún método, **sobrescribe el default**. Sin tocar la jerarquía.

Vale la pena haber visto la versión clásica primero. Porque cuando veáis la versión con defaults, vais a entender **qué problema concreto resuelve** — no es magia por ser magia, es magia que paga.

## Resumen

- La implementación clásica es **legible, debuggeable y honesta**. No es código malo.
- Su problema es que **escala mal**: cuatro líneas repetidas por aggregate, multiplicadas por todos los aggregates del sistema.
- La clase base abstracta resuelve la repetición pero **gasta la herencia única** y **obliga a implementar todos los métodos**.
- En el siguiente documento usamos **`default methods`** para conseguir lo mismo sin esos costes.
