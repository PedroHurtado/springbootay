# Mappers manuales entre JPA y dominio

## Por qué mappers manuales

Hay frameworks (MapStruct, ModelMapper) que generan mappers automáticos por convención de nombres. Vamos a **no usarlos**. Razones:

1. **El mapping no es trivial.** Nuestro dominio tiene constructores estáticos (`Pizza.create`, `Ingredient.create`), campos finales, `Set` inmutables expuestos, y validaciones en constructor. Un mapper "por convención" no sabe que hay que llamar a `create()` ni que hay que pasar `ingredients` resueltas desde un set de ids.

2. **El mapper es código de producción**, no boilerplate. Decide cómo cruza la frontera entre dominio y persistencia. Esa decisión debe estar **explícita** y **revisable**, no oculta detrás de anotaciones que generan código en tiempo de compilación.

3. **Coste real bajo.** Una clase `Pizza` con 4 campos genera 20 líneas de mapper. Es código que se escribe una vez y se modifica raramente.

4. **Cero magia.** El debugging es trivial: clicas, llegas al método, lees lo que hace.

La regla: **mapper manual = código aburrido, predecible y obvio**.

## El dominio que tenemos

Recordatorio del dominio (sin tocar nada):

```java
public class Pizza extends AggregateBase {
    private String name;
    private String description;
    private String url;
    private final Set<Ingredient> ingredients = new HashSet<>();

    public static Pizza create(UUID id, String name, String description,
                               String url, Set<Ingredient> ingredients) { ... }

    public void update(String name, String description, String url,
                       Set<Ingredient> ingredients) { ... }
}

public class Ingredient extends EntityBase {
    private String name;
    private BigDecimal cost;

    public static Ingredient create(UUID id, String name, BigDecimal cost) { ... }
}
```

Tanto `Pizza` como `Ingredient` se construyen mediante fábrica estática. **No tienen setters públicos para id ni constructor sin argumentos**. Eso es intencional: el dominio es **inmutable desde fuera salvo a través de operaciones del dominio**.

## Las entidades JPA

JPA necesita constructor sin argumentos y campos modificables por reflexión:

```java
@Entity
@Table(name = "ingredient")
public class IngredientJpa {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal cost;

    protected IngredientJpa() { }

    public IngredientJpa(UUID id, String name, BigDecimal cost) {
        this.id = id;
        this.name = name;
        this.cost = cost;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getCost() { return cost; }

    public void setName(String name) { this.name = name; }
    public void setCost(BigDecimal cost) { this.cost = cost; }
}
```

```java
@Entity
@Table(name = "pizza")
public class PizzaJpa {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "url", nullable = false)
    private String url;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "pizza_ingredient",
        joinColumns = @JoinColumn(name = "pizza_id"),
        inverseJoinColumns = @JoinColumn(name = "ingredient_id")
    )
    private Set<IngredientJpa> ingredients = new HashSet<>();

    protected PizzaJpa() { }

    public PizzaJpa(UUID id, String name, String description, String url,
                    Set<IngredientJpa> ingredients) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.url = url;
        this.ingredients = ingredients;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUrl() { return url; }
    public Set<IngredientJpa> getIngredients() { return ingredients; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setUrl(String url) { this.url = url; }
    public void setIngredients(Set<IngredientJpa> ingredients) { this.ingredients = ingredients; }
}
```

Dos mundos paralelos:

| Dominio                          | JPA                              |
|----------------------------------|----------------------------------|
| `Pizza.create(...)`              | `new PizzaJpa(...)`              |
| `Set<Ingredient>` (inmutable)    | `Set<IngredientJpa>` (mutable)   |
| Validaciones en constructor      | Sin validaciones                 |
| Eventos de dominio               | (no aplica)                      |
| Sin anotaciones                  | `@Entity`, `@Table`, `@Column`   |

## El mapper

Una clase por entidad de dominio. `@Component` para que Spring lo inyecte.

### `IngredientMapper`

```java
@Component
public class IngredientMapper {

    public IngredientJpa toJpa(Ingredient ingredient) {
        return new IngredientJpa(
            ingredient.getId(),
            ingredient.getName(),
            ingredient.getCost()
        );
    }

    public Ingredient toDomain(IngredientJpa jpa) {
        return Ingredient.create(
            jpa.getId(),
            jpa.getName(),
            jpa.getCost()
        );
    }
}
```

Limpio, directo, sin secretos. **Esto es todo el mapper.**

### `PizzaMapper`

Aquí hay un detalle: `Pizza` tiene una colección de `Ingredient`. El mapper de Pizza necesita mapear esa colección y para eso delega en `IngredientMapper`.

```java
@Component
public class PizzaMapper {

    private final IngredientMapper ingredientMapper;

    public PizzaMapper(IngredientMapper ingredientMapper) {
        this.ingredientMapper = ingredientMapper;
    }

    public PizzaJpa toJpa(Pizza pizza) {
        var ingredients = pizza.getIngredients().stream()
            .map(ingredientMapper::toJpa)
            .collect(toSet());

        return new PizzaJpa(
            pizza.getId(),
            pizza.getName(),
            pizza.getDescription(),
            pizza.getUrl(),
            ingredients
        );
    }

    public Pizza toDomain(PizzaJpa jpa) {
        var ingredients = jpa.getIngredients().stream()
            .map(ingredientMapper::toDomain)
            .collect(toSet());

        return Pizza.create(
            jpa.getId(),
            jpa.getName(),
            jpa.getDescription(),
            jpa.getUrl(),
            ingredients
        );
    }
}
```

`PizzaMapper` reutiliza `IngredientMapper`. Composición trivial; Spring inyecta lo que haga falta.

## Asimetría intencional: `toJpa` vs `toDomain`

Si te fijas, en `toDomain` llamamos a `Pizza.create(...)`. Eso **dispara el evento `pizza.create`** (recordatorio: en `Pizza.create` hay un `add("pizza.create", pizza)`).

¿Es correcto disparar el evento al **leer** una pizza de BD? **No**. Esa pizza ya existe, no se está creando.

Tenemos dos opciones, ambas legítimas:

**Opción A: limpiar eventos tras mapear desde BD**

```java
public Pizza toDomain(PizzaJpa jpa) {
    var pizza = Pizza.create(jpa.getId(), jpa.getName(), /* ... */);
    pizza.clearEvents();
    return pizza;
}
```

Limpio pero feo: usar `create` cuando no estamos creando es semánticamente incorrecto.

**Opción B: añadir al dominio un `Pizza.hydrate(...)` específico para reconstrucción desde persistencia.**

Pero eso es **tocar el dominio**, y Pedro ha pedido explícitamente **no tocarlo**.

Por tanto, **Opción A**. Disciplina: en `toDomain`, siempre `clearEvents()` al final.

Actualicemos el mapper:

```java
public Pizza toDomain(PizzaJpa jpa) {
    var ingredients = jpa.getIngredients().stream()
        .map(ingredientMapper::toDomain)
        .collect(toSet());

    var pizza = Pizza.create(
        jpa.getId(),
        jpa.getName(),
        jpa.getDescription(),
        jpa.getUrl(),
        ingredients
    );
    pizza.clearEvents();
    return pizza;
}
```

Lo mismo en `IngredientMapper.toDomain` si `Ingredient` fuese aggregate (en este caso no lo es, no tiene eventos, no aplica).

## El detalle del JPA recuperado

Cuando hacemos `jpa.getIngredients()`, JPA puede devolvernos un proxy lazy. Si la sesión Hibernate ya está cerrada, `LazyInitializationException`. Esto se gestiona desde el repositorio, no desde el mapper:

```java
@Override
@Transactional(readOnly = true)
public Pizza get(UUID id) {
    return jpa.findByIdWithIngredients(id)
        .map(mapper::toDomain)
        .orElseThrow(() -> new EntityNotFoundException("Pizza", id));
}
```

Con `JOIN FETCH` cargamos el grafo en una sola query y evitamos la lazy. La regla: **el repositorio entrega al mapper un grafo completamente cargado**. El mapper no piensa en lazy ni en sesiones, solo en mapear.

## Update: la trampa del re-mapeo

Cuando actualizamos una entidad existente, hay dos formas:

**Forma 1: `save` con la nueva entidad JPA construida desde dominio.**

```java
@Override
public void update(Pizza pizza) {
    get(pizza.getId()); // verifica existencia
    jpa.save(mapper.toJpa(pizza));
}
```

JPA detecta que ya existe (mismo id) y hace `UPDATE`. Simple.

**Forma 2: cargar la JPA actual, modificarla en sitio.**

```java
@Override
public void update(Pizza pizza) {
    var existing = jpa.findById(pizza.getId())
        .orElseThrow(() -> new EntityNotFoundException("Pizza", pizza.getId()));

    existing.setName(pizza.getName());
    existing.setDescription(pizza.getDescription());
    existing.setUrl(pizza.getUrl());
    existing.setIngredients(/* mapear ingredientes */);
    // jpa.save no es necesario, dirty checking lo detecta
}
```

La forma 2 es más fiel al modelo de **Unit of Work** de Hibernate (dirty checking, change tracking). La forma 1 es más simple pero puede generar `DELETE` + `INSERT` en colecciones si JPA no detecta correctamente la equivalencia.

**Para esta sesión usamos Forma 1.** Es más legible, menos propensa a errores sutiles, y para el modelo que tenemos funciona correctamente. Cuando la entidad sea más compleja o haya colecciones con orden, evaluaremos Forma 2.

## Test del mapper

El test del mapper es trivial pero importante: garantiza que la simetría se mantiene cuando el dominio cambia.

```java
class IngredientMapperTest {

    private final IngredientMapper mapper = new IngredientMapper();

    @Test
    void round_trip_preserva_los_datos() {
        var original = Ingredient.create(UUID.randomUUID(), "Mozzarella", new BigDecimal("1.50"));

        var jpa = mapper.toJpa(original);
        var back = mapper.toDomain(jpa);

        assertEquals(original.getId(), back.getId());
        assertEquals(original.getName(), back.getName());
        assertEquals(original.getCost(), back.getCost());
    }
}
```

Un test por mapper. Cuando alguien añada un campo a `Ingredient` y olvide mapearlo, este test falla.

## Resumen

- Mappers **manuales**, una clase por entidad de dominio, anotada `@Component`.
- Dos métodos: `toJpa(domain)` y `toDomain(jpa)`.
- Los mappers se componen: `PizzaMapper` usa `IngredientMapper`.
- En `toDomain`, llamar a `clearEvents()` tras `create(...)` para no disparar eventos al reconstruir desde BD.
- El repositorio entrega al mapper grafos completos (sin lazy proxies).
- Update con `save(mapper.toJpa(domain))` por simplicidad.
- Test de round-trip por mapper.
- Sin MapStruct, sin ModelMapper, sin magia.
