# Métodos mágicos de Spring Data

Spring Data parsea el **nombre de método** de un repositorio y deriva la consulta SQL automáticamente. Es el mecanismo central de los repositorios derivados: no escribes JPQL ni SQL, escribes el nombre del método siguiendo una convención y Spring genera la consulta.

## El parser

Un repositorio Spring Data se declara como interfaz que extiende `JpaRepository<T, ID>`:

```java
public interface PizzaRepository extends JpaRepository<PizzaJpa, UUID> {

    List<PizzaJpa> findByName(String name);
    List<PizzaJpa> findByNameContaining(String fragment);
    List<PizzaJpa> findByCostGreaterThan(BigDecimal threshold);
}
```

Spring Data analiza el nombre del método **en tiempo de arranque** y construye la consulta. Si la sintaxis es incorrecta, falla al arrancar la aplicación, no en runtime.

## Estructura del nombre

```
<verbo> [<distinct>] [<top>] By <propiedad1> [<operador>] [And|Or <propiedad2> ...] [OrderBy <propiedad> Asc|Desc]
```

### Verbos

- `findBy...` — devuelve resultados.
- `getBy...`, `queryBy...`, `searchBy...`, `streamBy...`, `readBy...` — sinónimos de `findBy`.
- `existsBy...` — devuelve `boolean`.
- `countBy...` — devuelve `long`.
- `deleteBy...` / `removeBy...` — borra y devuelve `void` o `long`.

### Tipo de retorno

El verbo `findBy` admite varios tipos de retorno; Spring se ajusta a lo que declares:

```java
Optional<PizzaJpa> findById(UUID id);
List<PizzaJpa> findByName(String name);
Page<PizzaJpa> findByNameContaining(String fragment, Pageable pageable);
Stream<PizzaJpa> findByCostGreaterThan(BigDecimal threshold);
PizzaJpa findFirstByOrderByCreatedAtDesc();
```

Si el resultado puede ser cero o uno, devuelve `Optional<T>`. Si pueden ser varios, `List<T>`, `Set<T>`, `Page<T>` o `Stream<T>`.

## Operadores

Sobre cada propiedad puedes aplicar un operador que se traduce a SQL:

| Operador en el método      | SQL generado                |
|----------------------------|-----------------------------|
| `By<Propiedad>`            | `WHERE prop = ?`            |
| `By<Propiedad>Is`          | `WHERE prop = ?`            |
| `By<Propiedad>Not`         | `WHERE prop <> ?`           |
| `By<Propiedad>IsNull`      | `WHERE prop IS NULL`        |
| `By<Propiedad>IsNotNull`   | `WHERE prop IS NOT NULL`    |
| `By<Propiedad>GreaterThan` | `WHERE prop > ?`            |
| `By<Propiedad>GreaterThanEqual` | `WHERE prop >= ?`      |
| `By<Propiedad>LessThan`    | `WHERE prop < ?`            |
| `By<Propiedad>LessThanEqual` | `WHERE prop <= ?`         |
| `By<Propiedad>Between`     | `WHERE prop BETWEEN ? AND ?`|
| `By<Propiedad>In`          | `WHERE prop IN (?, ?, ...)` |
| `By<Propiedad>NotIn`       | `WHERE prop NOT IN (...)`   |
| `By<Propiedad>Like`        | `WHERE prop LIKE ?`         |
| `By<Propiedad>NotLike`     | `WHERE prop NOT LIKE ?`     |
| `By<Propiedad>StartingWith`| `WHERE prop LIKE '?%'`      |
| `By<Propiedad>EndingWith`  | `WHERE prop LIKE '%?'`      |
| `By<Propiedad>Containing`  | `WHERE prop LIKE '%?%'`     |
| `By<Propiedad>IgnoreCase`  | `WHERE LOWER(prop) = LOWER(?)` |
| `By<Propiedad>True`        | `WHERE prop = true`         |
| `By<Propiedad>False`       | `WHERE prop = false`        |

## Combinadores

`And`, `Or` permiten combinar varias propiedades:

```java
List<PizzaJpa> findByNameAndCostLessThan(String name, BigDecimal max);
List<PizzaJpa> findByNameOrDescriptionContaining(String name, String desc);
```

## Ordenación

```java
List<PizzaJpa> findByCostGreaterThanOrderByCostAsc(BigDecimal min);
List<PizzaJpa> findByNameOrderByCostDescNameAsc(String name);
```

También se puede inyectar `Sort` como parámetro:

```java
List<PizzaJpa> findByName(String name, Sort sort);

// invocación
repository.findByName("Margherita", Sort.by("cost").descending());
```

## Paginación

`Pageable` y `Page` son la pareja estándar:

```java
Page<PizzaJpa> findByNameContaining(String fragment, Pageable pageable);

// invocación
Page<PizzaJpa> page = repository.findByNameContaining("queso",
    PageRequest.of(0, 20, Sort.by("name")));

page.getContent();        // List<PizzaJpa>
page.getTotalElements();  // long
page.getTotalPages();     // int
page.hasNext();           // boolean
```

Si solo necesitas paginar sin contar el total, usa `Slice<T>` en lugar de `Page<T>` (evita el `COUNT(*)`, más barato).

## Limitar el número de resultados

`Top<N>` o `First<N>` limitan el resultado:

```java
List<PizzaJpa> findTop10ByOrderByCostDesc();
Optional<PizzaJpa> findFirstByNameOrderByCreatedAtDesc(String name);
```

## Distinct

```java
List<PizzaJpa> findDistinctByIngredientsName(String ingredientName);
```

Útil cuando un `JOIN` produce duplicados.

## Navegación por propiedades anidadas

Puedes navegar relaciones con `_` o con CamelCase:

```java
List<PizzaJpa> findByIngredients_Name(String ingredientName);
List<PizzaJpa> findByIngredientsName(String ingredientName);
```

Genera un `JOIN` automático sobre la relación `ingredients` y filtra por `name`. Las dos formas son equivalentes; el guion bajo se reserva para casos donde haya ambigüedad entre nombres de propiedad.

## Existencia y conteo

```java
boolean existsByName(String name);
long countByCostGreaterThan(BigDecimal threshold);
```

`existsBy...` es la forma idiomática de comprobar existencia. Evita `findBy... != null` o cargar la entidad solo para descartarla. La traducción SQL es `SELECT 1 FROM ...` con `LIMIT 1`, mucho más barata.

## Borrado derivado

```java
@Transactional
long deleteByName(String name);
```

Spring genera `DELETE FROM ... WHERE name = ?`. Requiere `@Transactional` (o que el método se llame dentro de una transacción).

Importante: este `DELETE` se ejecuta **directamente en BD**, sin pasar por el ciclo de vida JPA. No se disparan callbacks `@PreRemove` ni cascadas. Si necesitas el ciclo completo, carga primero y borra después.

## Cuándo el método mágico se queda corto

Spring Data deriva consultas simples. Para casos complejos:

### `@Query` con JPQL

```java
@Query("SELECT p FROM PizzaJpa p WHERE p.cost > :min AND SIZE(p.ingredients) > :minIngredients")
List<PizzaJpa> findExpensiveWithManyIngredients(
    @Param("min") BigDecimal min,
    @Param("minIngredients") int minIngredients
);
```

### `@Query` nativo

```java
@Query(
    value = "SELECT * FROM pizza WHERE cost > ?1 ORDER BY RANDOM() LIMIT ?2",
    nativeQuery = true
)
List<PizzaJpa> findRandomExpensive(BigDecimal min, int limit);
```

Cuando uses SQL nativo, pierdes portabilidad entre motores y pierdes la traducción JPA, pero ganas acceso a funciones específicas del motor (window functions, `RANDOM()`, etc.).

### `@Modifying`

Para `UPDATE` o `DELETE` en `@Query`:

```java
@Modifying
@Transactional
@Query("UPDATE PizzaJpa p SET p.cost = p.cost * :factor WHERE p.cost < :threshold")
int applyPriceIncrease(@Param("factor") BigDecimal factor, @Param("threshold") BigDecimal threshold);
```

## Regla práctica

- Para consultas de **una o dos condiciones**: método derivado.
- Para consultas con **tres o más condiciones**, joins explícitos o agregaciones: `@Query` con JPQL.
- Para consultas que requieren funciones del motor: `@Query` nativo, comentado.
- Para consultas dinámicas (filtros opcionales): `Specification<T>` o `Querydsl` — fuera del alcance de esta sesión.

El método derivado es elegante para casos simples, pero **nombres de método largos son una señal de alarma**: si necesitas `findByNameAndCostGreaterThanAndIngredientsNameInOrderByCostDescNameAsc`, ese método pide a gritos un `@Query`.

## Resumen

- Spring Data parsea el nombre del método y genera la consulta.
- `findBy`, `existsBy`, `countBy`, `deleteBy` son los verbos.
- Operadores: `GreaterThan`, `Between`, `In`, `Like`, `Containing`, `IgnoreCase`, etc.
- `And`/`Or` combinan condiciones.
- `OrderBy` ordena; `Top<N>`/`First<N>` limitan; `Pageable` pagina.
- Navegación por propiedades anidadas con `_` o CamelCase.
- Si el nombre se vuelve largo, pasa a `@Query`.
