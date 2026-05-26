# Anotaciones JPA — referencia rápida

Este documento es una referencia compacta de las anotaciones JPA que vamos a usar. No pretende sustituir a la especificación ni a la documentación de Hibernate: es el subset que aparece en código real y que conviene tener fresco. Para profundizar, revisad el repo de Hibernate/JPA que os pasaré aparte.

## Marcado de entidad

```java
@Entity
@Table(name = "pizza")
public class PizzaJpa {
    // ...
}
```

- `@Entity` — declara que la clase es una entidad JPA gestionada por el `EntityManager`. Sin esta anotación, JPA no la ve.
- `@Table(name = "...")` — nombre de la tabla en BD. Si se omite, JPA usa el nombre de la clase. Lo declaramos siempre explícito: el nombre de la tabla es contrato de BD, no debe depender del nombre Java de la clase.

`@Table` admite también `schema`, `catalog` y `uniqueConstraints`:

```java
@Table(
    name = "ingredient",
    uniqueConstraints = @UniqueConstraint(columnNames = "name")
)
```

## Identificador

```java
@Id
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

- `@Id` — marca el campo como clave primaria. Es obligatorio en toda entidad.
- `@GeneratedValue` — pide a JPA que genere el valor. Estrategias: `IDENTITY`, `SEQUENCE`, `TABLE`, `AUTO`, `UUID`.

En nuestro dominio el id es un `UUID` generado por la propia entidad de dominio (`Pizza.create(UUID.randomUUID(), ...)`), así que **no usamos `@GeneratedValue`**. El id viene ya construido desde el dominio.

```java
@Id
@Column(name = "id", nullable = false, updatable = false)
private UUID id;
```

Si quisiéramos delegar la generación en BD:

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

Pero para nuestro modelo, el dominio es quien decide su id. Esa decisión vive en `Pizza.create()`, no en la capa de persistencia.

## Columnas

```java
@Column(name = "name", nullable = false, length = 100)
private String name;

@Column(name = "cost", nullable = false, precision = 10, scale = 2)
private BigDecimal cost;
```

- `name` — nombre de la columna en BD.
- `nullable` — si admite `NULL`. Por defecto `true`.
- `length` — longitud máxima para `VARCHAR`. Por defecto 255.
- `precision` y `scale` — para `BigDecimal`/`DECIMAL`. `precision` es el número total de dígitos, `scale` los decimales.
- `updatable` — si JPA emite la columna en `UPDATE`. Útil en ids o campos inmutables.
- `insertable` — análogo para `INSERT`.

Como con `@Table`, **nombramos siempre las columnas explícitamente**. El nombre físico es contrato.

## Relaciones

JPA modela cuatro tipos de relación:

- `@OneToOne`
- `@OneToMany`
- `@ManyToOne`
- `@ManyToMany`

En nuestro dominio, `Pizza` tiene una colección de `Ingredient`. Modelado en JPA como `@ManyToMany`:

```java
@Entity
@Table(name = "pizza")
public class PizzaJpa {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "pizza_ingredient",
        joinColumns = @JoinColumn(name = "pizza_id"),
        inverseJoinColumns = @JoinColumn(name = "ingredient_id")
    )
    private Set<IngredientJpa> ingredients = new HashSet<>();
}
```

- `@JoinTable` — tabla intermedia para la relación `N:M`. Se declara solo en uno de los dos lados (el dueño de la relación).
- `@JoinColumn` — nombre de la columna FK.
- `fetch` — `LAZY` (carga bajo demanda) o `EAGER` (carga inmediata). Regla práctica: **`LAZY` por defecto en colecciones**; `EAGER` solo cuando lo necesitas siempre.

### `@ManyToOne`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id", nullable = false)
private CategoryJpa category;
```

El lado `@ManyToOne` es habitualmente el dueño de la relación (tiene la columna FK).

### `@OneToMany` con `mappedBy`

```java
@OneToMany(mappedBy = "pizza", cascade = CascadeType.ALL, orphanRemoval = true)
private Set<ToppingJpa> toppings = new HashSet<>();
```

- `mappedBy` — indica que esta es la parte **inversa** de la relación; el dueño es el campo `pizza` del lado `ManyToOne`.
- `cascade` — qué operaciones se propagan a la colección: `PERSIST`, `MERGE`, `REMOVE`, `REFRESH`, `DETACH`, `ALL`.
- `orphanRemoval = true` — si un hijo se quita de la colección, JPA emite `DELETE`. Útil para composiciones fuertes.

## Fetch y N+1

`FetchType.EAGER` en colecciones es un anti-patrón clásico: cada `findById` arrastra todas las relaciones, generando el problema **N+1** (una consulta por cada entidad cargada). Reglas:

- Colecciones (`@OneToMany`, `@ManyToMany`) → `LAZY`.
- Asociaciones simples (`@ManyToOne`, `@OneToOne`) → también `LAZY` por defecto, aunque JPA las pone `EAGER` por defecto. **Sobrescribir explícitamente a `LAZY`**.
- Cuando necesites cargar el grafo completo, usa `JOIN FETCH` en JPQL o `@EntityGraph` en Spring Data.

```java
@Query("SELECT p FROM PizzaJpa p LEFT JOIN FETCH p.ingredients WHERE p.id = :id")
Optional<PizzaJpa> findByIdWithIngredients(@Param("id") UUID id);
```

## Conversores

Cuando un tipo Java no mapea directamente a una columna SQL, se usa un `AttributeConverter`:

```java
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Money money) {
        return money == null ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(BigDecimal value) {
        return value == null ? null : new Money(value);
    }
}
```

`autoApply = true` lo activa para **todos** los atributos del tipo `Money`. Si no, hay que indicarlo campo a campo con `@Convert(converter = MoneyConverter.class)`.

## Constructores y JPA

JPA exige un constructor sin argumentos para poder instanciar entidades por reflexión. En las clases JPA que escribimos:

```java
@Entity
public class PizzaJpa {

    protected PizzaJpa() { } // requerido por JPA, no usar desde código de aplicación

    public PizzaJpa(UUID id, String name, /* ... */) {
        // constructor que sí usamos
    }
}
```

`protected` (o package-private) es suficiente y comunica claramente que **no es para uso del código de aplicación**, solo JPA.

## Lo que no vamos a usar

Por brevedad, no vamos a tocar en esta sesión:

- `@Inheritance` y estrategias de herencia (`SINGLE_TABLE`, `JOINED`, `TABLE_PER_CLASS`).
- `@Embeddable` / `@Embedded` (composición de value objects en la misma tabla).
- `@MappedSuperclass`.
- `@SecondaryTable`.
- `@Version` (optimistic locking).
- Anotaciones de cache de segundo nivel.

Están en el repo de referencia. Cuando los necesitéis en proyecto, ya tendréis el vocabulario base.

## Resumen

- `@Entity` + `@Table` para declarar la entidad y su tabla.
- `@Id` obligatorio; nuestro dominio genera el `UUID`, no JPA.
- `@Column` siempre con `name` explícito.
- Relaciones: `@OneToOne`, `@OneToMany`, `@ManyToOne`, `@ManyToMany`. `LAZY` por defecto en colecciones; sobrescribir a `LAZY` también las simples.
- `@JoinColumn` y `@JoinTable` para nombrar columnas FK y tablas intermedias.
- `cascade` y `orphanRemoval` para componer ciclos de vida.
- Constructor sin argumentos `protected` requerido por JPA.
